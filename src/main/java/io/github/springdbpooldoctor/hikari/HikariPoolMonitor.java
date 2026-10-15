package io.github.springdbpooldoctor.hikari;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.springdbpooldoctor.autoconfigure.SpringDbPoolDoctorProperties;
import io.github.springdbpooldoctor.core.PoolAnalyzer;
import io.github.springdbpooldoctor.core.PoolFinding;
import io.github.springdbpooldoctor.core.PoolSnapshot;
import io.github.springdbpooldoctor.instrumented.InstrumentedDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HikariPoolMonitor {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolMonitor.class);

    private final DataSource dataSource;
    private final PoolAnalyzer analyzer;
    private final SpringDbPoolDoctorProperties properties;
    private boolean loggedUnresolvable;

    public HikariPoolMonitor(DataSource dataSource, PoolAnalyzer analyzer,
                             SpringDbPoolDoctorProperties properties) {
        this.dataSource = dataSource;
        this.analyzer = analyzer;
        this.properties = properties;
    }

    public void scan() {
        HikariDataSource hikari = resolveHikari();
        if (hikari == null) {
            if (!loggedUnresolvable) {
                log.warn("spring-db-pool-doctor: DataSource is not a HikariDataSource and cannot be unwrapped; skipping pool metrics");
                loggedUnresolvable = true;
            }
            return;
        }
        if (hikari.isClosed()) return;
        HikariPoolMXBean bean = hikari.getHikariPoolMXBean();
        if (bean == null) return;

        String poolName = hikari.getPoolName() == null ? "unnamed" : hikari.getPoolName();
        PoolSnapshot snapshot = new PoolSnapshot(
            poolName,
            bean.getActiveConnections(), bean.getIdleConnections(), bean.getTotalConnections(),
            hikari.getMaximumPoolSize(), bean.getThreadsAwaitingConnection(),
            System.currentTimeMillis());
        for (PoolFinding finding : analyzer.analyze(snapshot)) {
            emit(poolName, finding);
        }
        if (dataSource instanceof InstrumentedDataSource instrumented) {
            for (PoolFinding finding : analyzer.analyzeInstrumented(
                    instrumented.getStats().snapshot(properties.getLeakThresholdMs()))) {
                emit(poolName, finding);
            }
        }
    }

    private void emit(String poolName, PoolFinding finding) {
        log.warn("spring-db-pool-doctor pool={} severity={} code={} summary={} evidence={} recommendation={}",
            poolName, finding.severity(), finding.code(), finding.summary(),
            finding.evidence(), finding.recommendation());
    }

    private HikariDataSource resolveHikari() {
        if (dataSource instanceof HikariDataSource h) return h;
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
        } catch (SQLException ignored) {
        }
        return null;
    }
}
