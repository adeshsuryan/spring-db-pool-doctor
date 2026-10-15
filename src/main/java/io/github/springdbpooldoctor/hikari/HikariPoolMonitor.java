package io.github.springdbpooldoctor.hikari;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.springdbpooldoctor.core.PoolAnalyzer;
import io.github.springdbpooldoctor.core.PoolFinding;
import io.github.springdbpooldoctor.core.PoolSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HikariPoolMonitor {
    private static final Logger log = LoggerFactory.getLogger(HikariPoolMonitor.class);
    private final HikariDataSource dataSource;
    private final PoolAnalyzer analyzer;

    public HikariPoolMonitor(HikariDataSource dataSource, PoolAnalyzer analyzer) {
        this.dataSource = dataSource;
        this.analyzer = analyzer;
    }

    public void scan() {
        if (dataSource.isClosed()) return;
        HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
        if (bean == null) return;
        PoolSnapshot snapshot = new PoolSnapshot(
            dataSource.getPoolName() == null ? "unnamed" : dataSource.getPoolName(),
            bean.getActiveConnections(), bean.getIdleConnections(), bean.getTotalConnections(),
            dataSource.getMaximumPoolSize(), bean.getThreadsAwaitingConnection(),
            System.currentTimeMillis());
        for (PoolFinding finding : analyzer.analyze(snapshot)) {
            log.warn("spring-db-pool-doctor pool={} severity={} code={} summary={} evidence={} recommendation={}",
                snapshot.poolName(), finding.severity(), finding.code(), finding.summary(),
                finding.evidence(), finding.recommendation());
        }
    }
}
