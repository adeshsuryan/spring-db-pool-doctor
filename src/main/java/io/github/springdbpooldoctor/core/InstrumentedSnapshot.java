package io.github.springdbpooldoctor.core;

import java.util.List;

/** Point-in-time view of instrumented acquisition/hold statistics for one pool. */
public record InstrumentedSnapshot(
        int acquisitionSamples,
        double acquisitionP50Ms, double acquisitionP95Ms, double acquisitionP99Ms,
        int holdSamples,
        double holdP50Ms, double holdP95Ms, double holdP99Ms,
        long acquisitionSuccessCount, long acquisitionFailureCount,
        long openBorrowCount, int borrowsOverLeakThreshold,
        List<BorrowRecord> oldestOverThreshold) {

    public InstrumentedSnapshot {
        oldestOverThreshold = List.copyOf(oldestOverThreshold);
    }
}
