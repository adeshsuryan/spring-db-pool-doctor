package io.github.springdbpooldoctor.core;

import io.github.springdbpooldoctor.autoconfigure.SpringDbPoolDoctorProperties;
import java.util.ArrayList;
import java.util.List;

public class PoolAnalyzer {
    private final SpringDbPoolDoctorProperties properties;
    public PoolAnalyzer(SpringDbPoolDoctorProperties properties) { this.properties = properties; }

    public List<PoolFinding> analyze(PoolSnapshot snapshot) {
        List<PoolFinding> findings = new ArrayList<>();
        if (snapshot.max() <= 0) {
            findings.add(new PoolFinding(PoolFinding.Severity.WARNING, "POOL_MAX_INVALID",
                "Pool maximum is unavailable or invalid", "max=" + snapshot.max(),
                "Verify that the selected pool exposes valid runtime statistics."));
            return findings;
        }
        int pct = snapshot.activePercent();
        if (pct >= properties.getActivePercentCritical()) {
            findings.add(new PoolFinding(PoolFinding.Severity.CRITICAL, "POOL_NEAR_EXHAUSTION",
                "Connection pool is critically saturated",
                "active=" + snapshot.active() + ", idle=" + snapshot.idle() + ", total=" + snapshot.total()
                    + ", max=" + snapshot.max() + ", activePercent=" + pct + "%",
                "Inspect slow SQL, long transactions, lock waits and downstream calls made while holding a connection. Do not increase pool size without checking database capacity."));
        } else if (pct >= properties.getActivePercentWarning()) {
            findings.add(new PoolFinding(PoolFinding.Severity.WARNING, "POOL_HIGH_UTILIZATION",
                "Connection pool utilization is high",
                "active=" + snapshot.active() + ", idle=" + snapshot.idle() + ", max=" + snapshot.max()
                    + ", activePercent=" + pct + "%",
                "Correlate with request latency, database load and acquisition timeouts."));
        }
        if (snapshot.waitingThreads() > 0) {
            PoolFinding.Severity severity = pct >= properties.getActivePercentCritical()
                ? PoolFinding.Severity.CRITICAL : PoolFinding.Severity.WARNING;
            findings.add(new PoolFinding(severity, "THREADS_WAITING_FOR_CONNECTION",
                "Application threads are waiting for a database connection",
                "waitingThreads=" + snapshot.waitingThreads() + ", active=" + snapshot.active()
                    + ", max=" + snapshot.max(),
                "Inspect connection hold duration, SQL latency, transaction boundaries and database concurrency limits."));
        }
        if (findings.isEmpty() && properties.isLogHealthySnapshots()) {
            findings.add(new PoolFinding(PoolFinding.Severity.INFO, "POOL_HEALTHY",
                "No configured pool-pressure threshold is exceeded in this snapshot",
                "active=" + snapshot.active() + ", idle=" + snapshot.idle() + ", total=" + snapshot.total()
                    + ", max=" + snapshot.max(),
                "Continue monitoring under representative load; one snapshot does not prove overall health."));
        }
        return findings;
    }
}
