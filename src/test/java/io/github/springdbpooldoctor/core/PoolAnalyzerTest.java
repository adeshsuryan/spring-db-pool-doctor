package io.github.springdbpooldoctor.core;

import io.github.springdbpooldoctor.autoconfigure.SpringDbPoolDoctorProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoolAnalyzerTest {
    @Test void reportsCriticalSaturationAndWaitingThreads() {
        var properties = new SpringDbPoolDoctorProperties();
        var analyzer = new PoolAnalyzer(properties);
        var findings = analyzer.analyze(new PoolSnapshot("test", 10, 0, 10, 10, 3, 1L));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("POOL_NEAR_EXHAUSTION")));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("THREADS_WAITING_FOR_CONNECTION")));
    }

    @Test void noFindingBelowThreshold() {
        var analyzer = new PoolAnalyzer(new SpringDbPoolDoctorProperties());
        assertTrue(analyzer.analyze(new PoolSnapshot("test", 2, 8, 10, 10, 0, 1L)).isEmpty());
    }

    @Test void warnsAtConfiguredThreshold() {
        var properties = new SpringDbPoolDoctorProperties();
        var analyzer = new PoolAnalyzer(properties);
        var findings = analyzer.analyze(new PoolSnapshot("test", 8, 2, 10, 10, 0, 1L));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("POOL_HIGH_UTILIZATION")));
    }
}
