package io.github.springdbpooldoctor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.springdbpooldoctor.autoconfigure.SpringDbPoolDoctorProperties;
import io.github.springdbpooldoctor.core.FindingDeduper;
import io.github.springdbpooldoctor.core.PoolAnalyzer;
import io.github.springdbpooldoctor.core.PoolFinding;
import io.github.springdbpooldoctor.core.PoolSnapshot;
import io.github.springdbpooldoctor.instrumented.InstrumentedDataSource;
import io.github.springdbpooldoctor.core.InstrumentedStats;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration scenarios against a real PostgreSQL + real HikariCP pool.
 * Runs under failsafe (mvn verify). Requires a Postgres instance:
 *
 *   docker run -d --name sdbpd-postgres \
 *     -e POSTGRES_DB=pooltest -e POSTGRES_USER=doctor -e POSTGRES_PASSWORD=doctor \
 *     -p 5443:5432 postgres:16-alpine
 *
 * Override with IT_JDBC_URL / IT_DB_USER / IT_DB_PASSWORD. Skips if unreachable.
 */
class PoolSaturationIT {

    private static final String JDBC_URL =
            System.getenv().getOrDefault("IT_JDBC_URL", "jdbc:postgresql://localhost:5443/pooltest");
    private static final String DB_USER = System.getenv().getOrDefault("IT_DB_USER", "doctor");
    private static final String DB_PASS = System.getenv().getOrDefault("IT_DB_PASSWORD", "doctor");

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(databaseReachable(),
            "Skipping integration tests: no Postgres at " + JDBC_URL + " (start sdbpd-postgres)");
    }

    private static boolean databaseReachable() {
        try (Connection c = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private HikariConfig hikariConfig(int max, long connectionTimeoutMs) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(JDBC_URL);
        cfg.setUsername(DB_USER);
        cfg.setPassword(DB_PASS);
        cfg.setMaximumPoolSize(max);
        cfg.setConnectionTimeout(connectionTimeoutMs);
        cfg.setPoolName("it-pool");
        return cfg;
    }

    private PoolSnapshot snapshot(HikariDataSource ds) {
        var mx = ds.getHikariPoolMXBean();
        return new PoolSnapshot(ds.getPoolName(),
                mx.getActiveConnections(), mx.getIdleConnections(), mx.getTotalConnections(),
                ds.getMaximumPoolSize(), mx.getThreadsAwaitingConnection(),
                System.currentTimeMillis());
    }

    @Test
    void healthyPoolProducesNoFindings() throws Exception {
        try (HikariDataSource ds = new HikariDataSource(hikariConfig(5, 3000))) {
            try (Connection c = ds.getConnection()) { c.isValid(1); }
            PoolAnalyzer analyzer = new PoolAnalyzer(new SpringDbPoolDoctorProperties());
            var findings = analyzer.analyze(snapshot(ds));
            assertTrue(findings.stream().noneMatch(f -> f.severity() != PoolFinding.Severity.INFO));
        }
    }

    @Test
    void saturationProducesNearExhaustionAndWaitingThreads() throws Exception {
        try (HikariDataSource ds = new HikariDataSource(hikariConfig(3, 800))) {
            ExecutorService pool = Executors.newFixedThreadPool(6);
            CountDownLatch held = new CountDownLatch(3);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(pool.submit(() -> {
                    try (Connection c = ds.getConnection()) {
                        held.countDown();
                        Thread.sleep(3000);
                    } catch (Exception ignored) {}
                }));
            }
            assertTrue(held.await(5, TimeUnit.SECONDS));

            PoolAnalyzer analyzer = new PoolAnalyzer(new SpringDbPoolDoctorProperties());
            var findings = analyzer.analyze(snapshot(ds));
            assertTrue(findings.stream().anyMatch(f -> f.code().equals("POOL_NEAR_EXHAUSTION")),
                    "expected POOL_NEAR_EXHAUSTION, got " + findings);

            for (int i = 0; i < 3; i++) {
                futures.add(pool.submit(() -> {
                    try (Connection c = ds.getConnection()) { c.isValid(1); }
                    catch (SQLException expected) {}
                }));
            }
            Thread.sleep(400);
            var waitingFindings = analyzer.analyze(snapshot(ds));
            assertTrue(waitingFindings.stream()
                    .anyMatch(f -> f.code().equals("THREADS_WAITING_FOR_CONNECTION")));

            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void unreturnedConnectionAgesPastLeakThreshold() throws Exception {
        try (HikariDataSource hikari = new HikariDataSource(hikariConfig(3, 3000))) {
            InstrumentedStats stats = new InstrumentedStats(1.0);
            InstrumentedDataSource ds = new InstrumentedDataSource(hikari, stats);
            Connection leaked = ds.getConnection();
            Thread.sleep(50);
            var snap = stats.snapshot(10);
            assertEquals(1, snap.borrowsOverLeakThreshold());
            assertNotNull(snap.oldestOverThreshold().get(0).borrowStack());
            leaked.close();
        }
    }

    @Test
    void creationFailureCountedOnBadCredentials() {
        HikariConfig cfg = hikariConfig(2, 1000);
        cfg.setPassword("definitely-wrong");
        cfg.setInitializationFailTimeout(-1);
        try (HikariDataSource hikari = new HikariDataSource(cfg)) {
            InstrumentedStats stats = new InstrumentedStats(0.0);
            InstrumentedDataSource ds = new InstrumentedDataSource(hikari, stats);
            assertThrows(SQLException.class, ds::getConnection);
            assertTrue(stats.snapshot(60000).acquisitionFailureCount() >= 1);
        }
    }

    @Test
    void deduperEmitsRecoveryAfterSaturationClears() throws Exception {
        FindingDeduper deduper = new FindingDeduper(1);
        PoolAnalyzer analyzer = new PoolAnalyzer(new SpringDbPoolDoctorProperties());
        try (HikariDataSource ds = new HikariDataSource(hikariConfig(2, 500))) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch held = new CountDownLatch(2);
            pool.submit(() -> { try (Connection c = ds.getConnection()) { held.countDown(); Thread.sleep(1500); } catch (Exception ignored) {} });
            pool.submit(() -> { try (Connection c = ds.getConnection()) { held.countDown(); Thread.sleep(1500); } catch (Exception ignored) {} });
            assertTrue(held.await(5, TimeUnit.SECONDS));

            var fired = deduper.process("it-pool", analyzer.analyze(snapshot(ds)));
            assertTrue(fired.stream().anyMatch(f -> f.code().equals("POOL_NEAR_EXHAUSTION")),
                    "expected POOL_NEAR_EXHAUSTION, got " + fired);

            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            Thread.sleep(300);

            var cleared = deduper.process("it-pool", analyzer.analyze(snapshot(ds)));
            assertTrue(cleared.stream().anyMatch(f -> f.code().equals("FINDING_RECOVERED")),
                    "expected FINDING_RECOVERED, got " + cleared);
        }
    }
}
