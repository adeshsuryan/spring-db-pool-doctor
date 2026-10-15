package io.github.springdbpooldoctor.report;

import static org.junit.jupiter.api.Assertions.*;

import io.github.springdbpooldoctor.core.PoolFinding;
import io.github.springdbpooldoctor.core.PoolSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonReportWriterTest {

    @Test
    void writesValidJsonWithFindings() {
        PoolSnapshot snap = new PoolSnapshot("orders-pool", 20, 0, 20, 20, 7, 1234L);
        PoolFinding f = new PoolFinding(PoolFinding.Severity.CRITICAL, "POOL_NEAR_EXHAUSTION",
                "critically saturated", "active=20, max=20", "check next steps");
        PoolDoctorReport report = new PoolDoctorReport(999L,
                List.of(new PoolDoctorReport.PoolEntry("orders-pool", snap, List.of(f), List.of())));

        String json = JsonReportWriter.toJson(report);
        assertTrue(json.contains("\"poolName\":\"orders-pool\""));
        assertTrue(json.contains("\"ruleCode\":\"POOL_NEAR_EXHAUSTION\""));
        assertTrue(json.contains("\"active\":20"));
        assertTrue(json.startsWith("{\"generatedAt\":999"));
    }

    @Test
    void escapesQuotesAndNewlines() {
        PoolFinding f = new PoolFinding(PoolFinding.Severity.INFO, "X",
                "says \"hi\"\nnext", "e\\v", "r");
        PoolDoctorReport report = new PoolDoctorReport(1L,
                List.of(new PoolDoctorReport.PoolEntry("p", null, List.of(f), List.of())));
        String json = JsonReportWriter.toJson(report);
        assertTrue(json.contains("\\\"hi\\\"\\n"));
        assertTrue(json.contains("e\\\\v"));
    }

    @Test
    void recoveredCodesSerialized() {
        PoolDoctorReport report = new PoolDoctorReport(1L,
                List.of(new PoolDoctorReport.PoolEntry("p", null, List.of(),
                        List.of("Pool condition cleared: POOL_HIGH_UTILIZATION"))));
        assertTrue(JsonReportWriter.toJson(report).contains("\"recovered\":[\"Pool condition cleared: POOL_HIGH_UTILIZATION\"]"));
    }
}
