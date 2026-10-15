# Contributing to spring-db-pool-doctor

## Ground rules

1. Every rule needs a positive fixture AND a negative fixture.
2. Rule IDs are frozen once published — never rename or reuse them.
3. Reports distinguish measured fact / likely cause / suggested investigation.
4. No bind values, SQL text, connection URLs, or request IDs in findings or metrics.
5. Instrumentation must fail open: an exception in the wrapper returns the real connection immediately.
6. Zero required runtime dependencies in core where achievable; every new dependency gets a `SECURITY.md` line and a changelog line.

## Build

```bash
mvn clean verify
```

Requires JDK 17+ and Maven 3.9+.

## Commit attribution

All commits are authored solely by the repository owner. Do not add `Co-Authored-By` or tool-attribution lines.
