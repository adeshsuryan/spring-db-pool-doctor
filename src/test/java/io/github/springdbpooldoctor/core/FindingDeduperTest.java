package io.github.springdbpooldoctor.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FindingDeduperTest {

    private PoolFinding warn(String code) {
        return new PoolFinding(PoolFinding.Severity.WARNING, code, "s", "e", "r");
    }

    @Test
    void findingFiresOnlyAfterConsecutiveSamples() {
        FindingDeduper d = new FindingDeduper(3);
        assertTrue(d.process("p1", List.of(warn("POOL_NEAR_EXHAUSTION"))).isEmpty());
        assertTrue(d.process("p1", List.of(warn("POOL_NEAR_EXHAUSTION"))).isEmpty());
        var third = d.process("p1", List.of(warn("POOL_NEAR_EXHAUSTION")));
        assertEquals(1, third.size());
        assertEquals("POOL_NEAR_EXHAUSTION", third.get(0).code());
    }

    @Test
    void nonConsecutiveViolationsDoNotFire() {
        FindingDeduper d = new FindingDeduper(2);
        assertTrue(d.process("p1", List.of(warn("X"))).isEmpty());
        assertTrue(d.process("p1", List.of()).isEmpty()); // gap resets
        assertTrue(d.process("p1", List.of(warn("X"))).isEmpty());
    }

    @Test
    void recoveryEmittedOnceAfterRuleClears() {
        FindingDeduper d = new FindingDeduper(1);
        assertEquals(1, d.process("p1", List.of(warn("POOL_HIGH_UTILIZATION"))).size());
        var cleared = d.process("p1", List.of());
        assertEquals(1, cleared.size());
        assertEquals("FINDING_RECOVERED", cleared.get(0).code());
        assertTrue(d.process("p1", List.of()).isEmpty()); // no repeat recovery
    }

    @Test
    void activeFindingsTrackedPerPool() {
        FindingDeduper d = new FindingDeduper(1);
        d.process("p1", List.of(warn("A")));
        d.process("p2", List.of(warn("B")));
        assertEquals(2, d.activeFindings().size());
        assertTrue(d.activeFindings().containsKey("p1:A"));
        assertTrue(d.activeFindings().containsKey("p2:B"));
    }
}
