package io.github.springdbpooldoctor.core;

public record PoolSnapshot(String poolName, int active, int idle, int total, int max,
                           int waitingThreads, long capturedAtEpochMs) {
    public int activePercent() {
        return max <= 0 ? 0 : (int) Math.round(active * 100.0 / max);
    }
}
