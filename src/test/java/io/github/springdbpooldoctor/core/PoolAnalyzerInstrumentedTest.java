package io.github.springdbpooldoctor.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.springdbpooldoctor.autoconfigure.SpringDbPoolDoctorProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoolAnalyzerInstrumentedTest {

    private InstrumentedSnapshot snap(double acqP95, double holdP95, int overThreshold) {
        return new InstrumentedSnapshot(
                100, acqP95 / 2, acqP95, acqP95 * 2,
                100, holdP95 / 2, holdP95, holdP95 * 2,
                90, 2, overThreshold, overThreshold, List.of());
    }

    @Test
    void noFindingsWhenInstrumentedStatsAreHealthy() {
        var analyzer = new PoolAnalyzer(new SpringDbPoolDoctorProperties());
        assertTrue(analyzer.analyzeInstrumented(snap(50, 100, 0)).isEmpty());
    }

    @Test
    void acquisitionLatencyRuleFiresAboveThreshold() {
        var props = new SpringDbPoolDoctorProperties();
        props.setAcquisitionWarnMs(200);
        var analyzer = new PoolAnalyzer(props);
        var findings = analyzer.analyzeInstrumented(snap(500, 10, 0));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("ACQUISITION_LATENCY_HIGH")));
    }

    @Test
    void holdDurationRuleFiresAboveThreshold() {
        var props = new SpringDbPoolDoctorProperties();
        props.setHoldWarnMs(500);
        var analyzer = new PoolAnalyzer(props);
        var findings = analyzer.analyzeInstrumented(snap(10, 3000, 0));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("CONNECTION_HOLD_DURATION_HIGH")));
    }

    @Test
    void possibleLeakUsesSuspicionLanguage() {
        var analyzer = new PoolAnalyzer(new SpringDbPoolDoctorProperties());
        var findings = analyzer.analyzeInstrumented(snap(10, 10, 2));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("POSSIBLE_LEAK_DETECTED")));
        assertTrue(findings.stream().anyMatch(f -> f.recommendation().contains("not proof of a leak")));
    }
}
