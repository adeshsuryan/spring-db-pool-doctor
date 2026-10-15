package io.github.springdbpooldoctor.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies consecutive-sample gating, cooldown deduplication and recovery
 * notices on top of the raw per-snapshot rule results. A rule must violate in
 * N consecutive scans before its finding is emitted; once a fired rule stops
 * violating, a FINDING_RECOVERED info notice is emitted exactly once.
 */
public class FindingDeduper {

    private static final long DEFAULT_COOLDOWN_MS = 60_000;

    private final int consecutiveSamples;
    private final long cooldownMs;
    private final Map<String, Integer> consecutiveCount = new HashMap<>();
    private final Map<String, Long> firedAtMs = new HashMap<>();
    private final Map<String, PoolFinding> activeFindings = new HashMap<>();

    public FindingDeduper(int consecutiveSamples) {
        this(consecutiveSamples, DEFAULT_COOLDOWN_MS);
    }

    public FindingDeduper(int consecutiveSamples, long cooldownMs) {
        this.consecutiveSamples = Math.max(1, consecutiveSamples);
        this.cooldownMs = Math.max(0, cooldownMs);
    }

    /** Returns the findings to emit for this scan (new findings + recoveries). */
    public synchronized List<PoolFinding> process(String poolName, List<PoolFinding> rawFindings) {
        List<PoolFinding> emit = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (PoolFinding f : rawFindings) {
            if ("POOL_HEALTHY".equals(f.code())) { emit.add(f); continue; }
            String key = poolName + ':' + f.code();
            seen.add(key);
            int count = consecutiveCount.merge(key, 1, Integer::sum);
            if (count >= consecutiveSamples) {
                Long fired = firedAtMs.get(key);
                long now = System.currentTimeMillis();
                if (fired == null) {
                    firedAtMs.put(key, now);
                    activeFindings.put(key, f);
                    emit.add(f);
                } else if (now - fired >= cooldownMs) {
                    firedAtMs.put(key, now);
                    emit.add(f); // still firing past cooldown — reminder
                }
            }
        }

        // recovery: previously fired keys that are absent now
        List<String> recoveredKeys = new ArrayList<>();
        for (String key : firedAtMs.keySet()) {
            if (key.startsWith(poolName + ':') && !seen.contains(key)) {
                PoolFinding original = activeFindings.remove(key);
                recoveredKeys.add(key);
                emit.add(new PoolFinding(PoolFinding.Severity.INFO, "FINDING_RECOVERED",
                    "Pool condition cleared: " + key.substring(poolName.length() + 1),
                    "pool=" + poolName + (original != null ? ", previousEvidence=" + original.evidence() : ""),
                    "Continue monitoring; a recovery does not prove the root cause is resolved."));
            }
        }
        recoveredKeys.forEach(firedAtMs::remove);
        consecutiveCount.keySet().removeIf(k -> k.startsWith(poolName + ':') && !seen.contains(k));
        return emit;
    }

    /** Currently active (fired, not yet cleared) findings per pool. */
    public synchronized Map<String, PoolFinding> activeFindings() {
        return Map.copyOf(activeFindings);
    }
}
