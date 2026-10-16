# Changelog

All notable changes to spring-db-pool-doctor are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/); versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added — Phase 4 (v0.1.0-alpha.4)

- Integration test suite (`PoolSaturationIT`) against real PostgreSQL 16: saturation, threads-waiting, recovery and post-exhaustion health scenarios.
- Runnable `examples/demo-app` with docker-compose PostgreSQL fixture.
- JDK × Spring Boot compatibility matrix under `test-java-project/matrix-pool-doctor` (Docker builds, 17/21/25 × Boot 3.2–3.5).
- GitHub Actions CI workflow; OWASP dependency-check and SpotBugs audit profile (`-Paudit`) with reviewed exclude filter.
- Project banner (`docs/assets/banner.svg`) and verified-compatibility table in README.

### Fixed

- SpotBugs `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`: defensive copies in `InstrumentedSnapshot`, `PoolDoctorReport`/`PoolEntry` constructors and `BorrowRecord.borrowStack()`; documented exclusions for intentionally shared collaborators.

### Added — Phase 3 (v0.1.0-alpha.3)

- `PoolMonitorService` scanning **all** `DataSource` beans (multi-pool), resolving each to its `HikariPoolMXBean` — including wrapped/`InstrumentedDataSource` beans.
- `FindingDeduper`: consecutive-sample gating (`consecutive-samples`, default 3), 60s cooldown dedup, and `FINDING_RECOVERED` notices when a fired rule clears.
- `PoolDoctorReport` + dependency-free `JsonReportWriter`; optional `spring.db-pool-doctor.report.path` writes the JSON report on every scan.
- Optional `/actuator/dbPoolDoctor` endpoint via `spring.db-pool-doctor.actuator.enabled=true` (off by default; host must secure it).

### Added — Phase 2 (v0.1.0-alpha.2)

- `InstrumentedDataSource`: opt-in `spring.db-pool-doctor.instrumented=true` wraps `DataSource` beans, times `getConnection()`, and records connection hold duration; fail-open on instrumentation errors.
- `InstrumentedStats`: bounded latency rings, open-borrow tracking, opt-in rate-limited stack sampling (`stack-sample-rate`).
- New findings: `ACQUISITION_LATENCY_HIGH`, `CONNECTION_HOLD_DURATION_HIGH`, `POSSIBLE_LEAK_DETECTED`.
- `DoctorDataSourcePostProcessor` (static, early) applies wrapping regardless of autoconfiguration order.

### Added — Phase 1 (v0.1.0-alpha.1)

- `PoolMonitorService`-equivalent sampling of `HikariPoolMXBean` statistics on a configurable interval.
- `PoolAnalyzer` rule engine: `POOL_MAX_INVALID`, `POOL_HIGH_UTILIZATION`, `POOL_NEAR_EXHAUSTION`, `THREADS_WAITING_FOR_CONNECTION`, `POOL_HEALTHY`.
- Spring Boot 3.x auto-configuration via `META-INF/spring/...AutoConfiguration.imports`.
- `spring.db-pool-doctor.*` configuration properties with metadata for IDE autocomplete.
- `PoolMonitorScheduler` with a self-managed daemon thread — no `@EnableScheduling` requirement on the host application.

### Security

- Read-only design: no pool mutation, no connection mutation, no bind-value capture, no request-ID metric labels.
