package io.github.springdbpooldoctor.core;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.springdbpooldoctor.autoconfigure.SpringDbPoolDoctorProperties;
import io.github.springdbpooldoctor.instrumented.InstrumentedDataSource;
import io.github.springdbpooldoctor.report.PoolDoctorReport;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans every DataSource bean, resolves each to its Hikari pool, and routes
 * raw findings through consecutive-sample gating and deduplication. Keeps the
 * latest report for programmatic or Actuator access.
 */
public class PoolMonitorService {

    private static final Logger log = LoggerFactory.getLogger(PoolMonitorService.class);

    private final List<DataSource> dataSources;
    private final PoolAnalyzer analyzer;
    private final SpringDbPoolDoctorProperties properties;
    private final FindingDeduper deduper;

    private volatile PoolDoctorReport latestReport;
    private volatile long lastScanEpochMs;
    private final Map<DataSource, Boolean> unresolvableLogged = new java.util.concurrent.ConcurrentHashMap<>();

    public PoolMonitorService(List<DataSource> dataSources, PoolAnalyzer analyzer,
                              SpringDbPoolDoctorProperties properties) {
        this.dataSources = List.copyOf(dataSources);
        this.analyzer = analyzer;
        this.properties = properties;
        this.deduper = new FindingDeduper(properties.getConsecutiveSamples());
    }

    public void scan() {
        List<PoolDoctorReport.PoolEntry> entries = new ArrayList<>();
        List<String> recovered = new ArrayList<>();
        for (DataSource ds : dataSources) {
            PoolDoctorReport.PoolEntry entry = scanOne(ds, recovered);
            if (entry != null) entries.add(entry);
        }
        lastScanEpochMs = System.currentTimeMillis();
        latestReport = new PoolDoctorReport(lastScanEpochMs, List.copyOf(entries));
        writeReportFile(latestReport);
    }

    private void writeReportFile(PoolDoctorReport report) {
        String path = properties.getReport().getPath();
        if (path == null || path.isBlank()) return;
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(path),
                    io.github.springdbpooldoctor.report.JsonReportWriter.toJson(report));
        } catch (java.io.IOException e) {
            log.warn("spring-db-pool-doctor: could not write report file {}: {}", path, e.toString());
        }
    }

    private PoolDoctorReport.PoolEntry scanOne(DataSource ds, List<String> recoveredOut) {
        HikariDataSource hikari = resolveHikari(ds);
        if (hikari == null) {
            if (unresolvableLogged.putIfAbsent(ds, true) == null) {
                log.warn("spring-db-pool-doctor: DataSource {} is not a HikariDataSource and cannot be unwrapped; skipping", ds.getClass().getName());
            }
            return null;
        }
        if (hikari.isClosed()) return null;
        HikariPoolMXBean bean = hikari.getHikariPoolMXBean();
        if (bean == null) return null;

        String poolName = hikari.getPoolName() == null ? "unnamed" : hikari.getPoolName();
        PoolSnapshot snapshot = new PoolSnapshot(
                poolName,
                bean.getActiveConnections(), bean.getIdleConnections(), bean.getTotalConnections(),
                hikari.getMaximumPoolSize(), bean.getThreadsAwaitingConnection(),
                System.currentTimeMillis());

        List<PoolFinding> raw = new ArrayList<>(analyzer.analyze(snapshot));
        if (ds instanceof InstrumentedDataSource instrumented) {
            raw.addAll(analyzer.analyzeInstrumented(
                    instrumented.getStats().snapshot(properties.getLeakThresholdMs())));
        }

        List<PoolFinding> emit = deduper.process(poolName, raw);
        List<String> recoveredCodes = new ArrayList<>();
        List<PoolFinding> findings = new ArrayList<>();
        for (PoolFinding f : emit) {
            if ("FINDING_RECOVERED".equals(f.code())) recoveredCodes.add(f.summary());
            else findings.add(f);
        }
        for (PoolFinding f : emit) {
            if ("INFO".equals(f.severity().name()) && !properties.isLogHealthySnapshots()
                    && !"FINDING_RECOVERED".equals(f.code())) {
                continue;
            }
            log.warn("spring-db-pool-doctor pool={} severity={} code={} summary={} evidence={} recommendation={}",
                    poolName, f.severity(), f.code(), f.summary(), f.evidence(), f.recommendation());
        }
        return new PoolDoctorReport.PoolEntry(poolName, snapshot, List.copyOf(findings), recoveredCodes);
    }

    private HikariDataSource resolveHikari(DataSource ds) {
        if (ds instanceof HikariDataSource h) return h;
        try {
            if (ds.isWrapperFor(HikariDataSource.class)) return ds.unwrap(HikariDataSource.class);
        } catch (SQLException ignored) {
        }
        return null;
    }

    public PoolDoctorReport latestReport() { return latestReport; }
    public long lastScanEpochMs() { return lastScanEpochMs; }

    /** Actuator/programmatic view: latest report as nested maps/lists. */
    public Map<String, Object> reportAsMap() {
        PoolDoctorReport report = latestReport;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAtEpochMs", lastScanEpochMs);
        List<Map<String, Object>> pools = new ArrayList<>();
        if (report != null) {
            for (PoolDoctorReport.PoolEntry p : report.pools()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("poolName", p.poolName());
                PoolSnapshot s = p.latestSnapshot();
                if (s != null) {
                    Map<String, Object> latest = new LinkedHashMap<>();
                    latest.put("active", s.active());
                    latest.put("idle", s.idle());
                    latest.put("total", s.total());
                    latest.put("max", s.max());
                    latest.put("waitingThreads", s.waitingThreads());
                    latest.put("activePercent", s.activePercent());
                    pm.put("latest", latest);
                }
                List<Map<String, Object>> fs = new ArrayList<>();
                for (PoolFinding f : p.findings()) {
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("ruleCode", f.code());
                    fm.put("severity", f.severity().name());
                    fm.put("summary", f.summary());
                    fm.put("evidence", f.evidence());
                    fm.put("recommendation", f.recommendation());
                    fs.add(fm);
                }
                pm.put("findings", fs);
                if (!p.recoveredCodes().isEmpty()) pm.put("recovered", p.recoveredCodes());
                pools.add(pm);
            }
        }
        root.put("pools", pools);
        return root;
    }
}
