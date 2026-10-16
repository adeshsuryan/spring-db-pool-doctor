![Spring DB Pool Doctor](docs/assets/banner.svg)

# Spring DB Pool Doctor

A HikariCP-first Spring Boot starter that samples connection-pool statistics and emits diagnostic findings for saturation, high utilization, and threads waiting for connections — with evidence and next-check guidance rather than raw metrics.

## Status
Early development (0.1.0-SNAPSHOT). Sampling mode, instrumented acquisition/hold timing, JSON report, optional Actuator endpoint and multi-pool support are implemented. See [CHANGELOG](CHANGELOG.md).

## Configuration
```yaml
spring:
  db-pool-doctor:
    enabled: true                 # default true
    scan-interval-ms: 10000       # sampling interval
    active-percent-warning: 80    # utilization warning threshold
    active-percent-critical: 95   # utilization critical threshold
    log-healthy-snapshots: false  # emit POOL_HEALTHY info findings
    consecutive-samples: 3        # sustained violations before a finding fires
    instrumented: false           # wrap DataSource to time getConnection()/hold
    stack-sample-rate: 0.1        # fraction of borrows that capture a stack
    acquisition-warn-ms: 3000     # p95 acquisition threshold
    hold-warn-ms: 2000            # p95 hold-duration threshold
    leak-threshold-ms: 60000      # open-borrow age → POSSIBLE_LEAK_DETECTED
    actuator:
      enabled: false              # /actuator/dbPoolDoctor — secure before use
    report:
      path:                       # optional JSON report file, rewritten each scan
```

The monitor runs on its own daemon scheduler — it does **not** enable or depend on Spring scheduling in your application.

## Requirements
- JDK 17+
- Spring Boot 3.2+
- HikariCP

Verified in CI-style Docker builds (all unit + integration tests against real PostgreSQL):

| JDK | Spring Boot | Result |
| --- | --- | --- |
| 17 | 3.2.12 / 3.3.13 / 3.4.10 / 3.5.7 | PASS |
| 21 | 3.2.12 / 3.3.13 / 3.4.10 / 3.5.7 | PASS |
| 25 | 3.5.7 | PASS |
| 25 | 3.2.x–3.4.x | Not supported upstream (Boot < 3.5 doesn't support Java 25) |

## Install

```xml
<dependency>
  <groupId>io.github.adeshsuryan</groupId>
  <artifactId>spring-db-pool-doctor-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Findings

| Code | Severity | Meaning |
| ---- | -------- | ------- |
| `POOL_MAX_INVALID` | WARNING | Pool max not exposed or invalid — check that a supported pool is in use |
| `POOL_HIGH_UTILIZATION` | WARNING | Active connections above the warning threshold |
| `POOL_NEAR_EXHAUSTION` | CRITICAL | Active connections above the critical threshold |
| `THREADS_WAITING_FOR_CONNECTION` | WARNING/CRITICAL | Application threads queued waiting for a connection |
| `ACQUISITION_LATENCY_HIGH` | WARNING | `getConnection()` p95 above threshold (instrumented mode) |
| `CONNECTION_HOLD_DURATION_HIGH` | WARNING | Connection hold p95 above threshold (instrumented mode) |
| `POSSIBLE_LEAK_DETECTED` | WARNING | Open borrows past `leak-threshold-ms`; sampled stack in evidence |
| `FINDING_RECOVERED` | INFO | A previously fired condition has cleared |
| `POOL_HEALTHY` | INFO | No threshold exceeded (only when `log-healthy-snapshots: true`) |

## Limitations and safety

- High utilization is a symptom, not proof of a leak — each finding includes next-check guidance.
- `POSSIBLE_LEAK_DETECTED` is suspicion language; a long hold can be a legitimate slow query or lock wait.
- Never captures SQL, bind values, connection URLs, or request identifiers.
- Read-only: never modifies pool settings or closes application-owned connections.
- Do not increase pool size without checking database connection limits and replica count.
- Non-Hikari pools (Tomcat JDBC, DBCP2) are not yet supported.

## Roadmap

1. ~~`DataSource`/`Connection` instrumentation~~ — done (opt-in).
2. ~~Time-window rules, deduplication, recovery notices~~ — done.
3. ~~JSON report + optional Actuator endpoint~~ — done.
4. ~~Multi-pool support~~ — done.
5. ~~Integration suite against real PostgreSQL + JDK/Spring Boot matrix~~ — done (external fixture; Testcontainers incompatible with Docker Desktop's socket proxy).
6. Overhead benchmarks; Tomcat JDBC / Commons DBCP2 adapters.
7. Maven Central release of 0.1.0.

## License

Apache-2.0 — see [LICENSE](LICENSE).

