package io.github.springdbpooldoctor.core;

public record PoolFinding(Severity severity, String code, String summary,
                          String evidence, String recommendation) {
    public enum Severity { INFO, WARNING, CRITICAL }
}
