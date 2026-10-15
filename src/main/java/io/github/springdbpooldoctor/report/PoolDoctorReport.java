package io.github.springdbpooldoctor.report;

import io.github.springdbpooldoctor.core.PoolFinding;
import io.github.springdbpooldoctor.core.PoolSnapshot;
import java.util.List;

/** Immutable report model; produced by PoolMonitorService, serialized by JsonReportWriter. */
public record PoolDoctorReport(long generatedAtEpochMs, List<PoolEntry> pools) {

    public record PoolEntry(String poolName, PoolSnapshot latestSnapshot,
                            List<PoolFinding> findings, List<String> recoveredCodes) {
    }
}
