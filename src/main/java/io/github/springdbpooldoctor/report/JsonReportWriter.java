package io.github.springdbpooldoctor.report;

import io.github.springdbpooldoctor.core.PoolFinding;
import io.github.springdbpooldoctor.core.PoolSnapshot;
import java.util.List;
import java.util.Map;

/** Minimal dependency-free JSON writer for pool reports. */
public final class JsonReportWriter {

    private JsonReportWriter() {}

    public static String toJson(PoolDoctorReport report) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"generatedAt\":").append(report.generatedAtEpochMs())
          .append(",\"pools\":[");
        List<PoolDoctorReport.PoolEntry> pools = report.pools();
        for (int i = 0; i < pools.size(); i++) {
            PoolDoctorReport.PoolEntry p = pools.get(i);
            if (i > 0) sb.append(',');
            sb.append('{').append("\"poolName\":").append(quoted(p.poolName()));
            PoolSnapshot s = p.latestSnapshot();
            if (s != null) {
                sb.append(",\"latest\":{")
                  .append("\"active\":").append(s.active())
                  .append(",\"idle\":").append(s.idle())
                  .append(",\"total\":").append(s.total())
                  .append(",\"max\":").append(s.max())
                  .append(",\"waitingThreads\":").append(s.waitingThreads())
                  .append(",\"activePercent\":").append(s.activePercent())
                  .append(",\"capturedAtEpochMs\":").append(s.capturedAtEpochMs())
                  .append('}');
            }
            sb.append(",\"findings\":[");
            List<PoolFinding> findings = p.findings();
            for (int j = 0; j < findings.size(); j++) {
                PoolFinding f = findings.get(j);
                if (j > 0) sb.append(',');
                sb.append('{')
                  .append("\"ruleCode\":").append(quoted(f.code()))
                  .append(",\"severity\":").append(quoted(f.severity().name()))
                  .append(",\"summary\":").append(quoted(f.summary()))
                  .append(",\"evidence\":").append(quoted(f.evidence()))
                  .append(",\"recommendation\":").append(quoted(f.recommendation()))
                  .append('}');
            }
            sb.append(']');
            if (!p.recoveredCodes().isEmpty()) {
                sb.append(",\"recovered\":[");
                for (int j = 0; j < p.recoveredCodes().size(); j++) {
                    if (j > 0) sb.append(',');
                    sb.append(quoted(p.recoveredCodes().get(j)));
                }
                sb.append(']');
            }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** For direct Map-based JSON (e.g. Actuator endpoint model). */
    public static String mapToJson(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, map);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { sb.append(quoted(s)); return; }
        if (v instanceof Number || v instanceof Boolean) { sb.append(v); return; }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quoted(String.valueOf(e.getKey()))).append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
            return;
        }
        sb.append(quoted(String.valueOf(v)));
    }

    private static String quoted(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
