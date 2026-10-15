# Spring DB Pool Doctor

A HikariCP-first Spring Boot starter that samples connection-pool statistics and emits diagnostic findings for saturation, high utilization, and threads waiting for connections — with evidence and next-check guidance rather than raw metrics.

## Status
Early development (0.1.0-SNAPSHOT). Sampling-mode diagnostics are implemented. Per-call acquisition latency and connection hold-duration instrumentation are on the roadmap — see [CHANGELOG](CHANGELOG.md) and issues for progress.

## Requirements
- JDK 17+
- Spring Boot 3.2+
- HikariCP

## Install

```xml
<dependency>
  <groupId>io.github.adeshsuryan</groupId>
  <artifactId>spring-db-pool-doctor-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Configuration
```yaml
spring:
  db-pool-doctor:
    enabled: true                 # default true
    scan-interval-ms: 10000       # sampling interval
    active-percent-warning: 80    # utilization warning threshold
    active-percent-critical: 95   # utilization critical threshold
    log-healthy-snapshots: false  # emit POOL_HEALTHY info findings
```

The monitor runs on its own daemon scheduler — it does **not** enable or depend on Spring scheduling in your application.

## Findings

| Code | Severity | Meaning |
| ---- | -------- | ------- |
| `POOL_MAX_INVALID` | WARNING | Pool max not exposed or invalid — check that a supported pool is in use |
| `POOL_HIGH_UTILIZATION` | WARNING | Active connections above the warning threshold |
| `POOL_NEAR_EXHAUSTION` | CRITICAL | Active connections above the critical threshold |
| `THREADS_WAITING_FOR_CONNECTION` | WARNING/CRITICAL | Application threads queued waiting for a connection |
| `POOL_HEALTHY` | INFO | No threshold exceeded (only when `log-healthy-snapshots: true`) |

## Limitations and safety

- Supports a single-candidate `HikariDataSource` bean in this version.
- High utilization is a symptom, not proof of a leak — each finding includes next-check guidance.
- Never captures SQL, bind values, connection URLs, or request identifiers.
- Read-only: never modifies pool settings or closes application-owned connections.
- Do not increase pool size without checking database connection limits and replica count.
- No Actuator endpoint, Micrometer metrics, JSON report, connection proxy, or non-Hikari adapters yet.

## Roadmap

1. `DataSource`/`Connection` instrumentation: acquisition wait time and connection hold duration (opt-in).
2. Time-window rules, deduplication, recovery notices.
3. JSON report + optional secured Actuator endpoint.
4. Multi-pool support and named-pool diagnostics.
5. Tomcat JDBC / Commons DBCP2 adapters.
6. Testcontainers integration suite and overhead benchmarks.

## License

Apache-2.0 — see [LICENSE](LICENSE).

