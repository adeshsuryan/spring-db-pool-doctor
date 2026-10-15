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

    /** Rules that only fire when instrumented mode captured timing data. */
    public List<PoolFinding> analyzeInstrumented(InstrumentedSnapshot s) {
        List<PoolFinding> findings = new ArrayList<>();
        if (s.acquisitionSamples() >= 10 && s.acquisitionP95Ms() >= properties.getAcquisitionWarnMs()) {
            findings.add(new PoolFinding(PoolFinding.Severity.WARNING, "ACQUISITION_LATENCY_HIGH",
                "Pool getConnection() p95 latency is elevated",
                "acquisitionP95Ms=" + s.acquisitionP95Ms() + ", acquisitionP99Ms=" + s.acquisitionP99Ms()
                    + ", samples=" + s.acquisitionSamples() + ", failures=" + s.acquisitionFailureCount(),
                "Distinguish waiting-for-pool from physical connection creation; check saturation, DNS/TLS, database failover and network path."));
        }
        if (s.holdSamples() >= 10 && s.holdP95Ms() >= properties.getHoldWarnMs()) {
            findings.add(new PoolFinding(PoolFinding.Severity.WARNING, "CONNECTION_HOLD_DURATION_HIGH",
                "Connections are being held longer than expected",
                "holdP95Ms=" + s.holdP95Ms() + ", holdP99Ms=" + s.holdP99Ms()
                    + ", samples=" + s.holdSamples() + ", openBorrows=" + s.openBorrowCount(),
                "Inspect slow SQL, transaction scope, remote calls inside transactions, lock waits and batch jobs."));
        }
        if (s.borrowsOverLeakThreshold() > 0) {
            StringBuilder evidence = new StringBuilder("openPastThreshold=")
                    .append(s.borrowsOverLeakThreshold());
            for (BorrowRecord b : s.oldestOverThreshold()) {
                StackTraceElement[] stack = b.borrowStack();
                if (stack != null && stack.length > 0) {
                    evidence.append("; borrowSite=").append(stack[0]);
                }
            }
            findings.add(new PoolFinding(PoolFinding.Severity.WARNING, "POSSIBLE_LEAK_DETECTED",
                "One or more borrowed connections exceed the leak threshold",
                evidence.toString(),
                "A long hold is not proof of a leak. Inspect the borrow site, exceptional paths and wrappers; verify try-with-resources coverage."));
        }
        return findings;
    }
}
