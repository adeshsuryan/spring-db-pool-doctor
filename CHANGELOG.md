# Changelog

All notable changes to spring-db-pool-doctor are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/); versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `HikariPoolMonitor` sampling `HikariPoolMXBean` statistics on a configurable interval.
- `PoolAnalyzer` rule engine with first rule set: `POOL_MAX_INVALID`, `POOL_HIGH_UTILIZATION`, `POOL_NEAR_EXHAUSTION`, `THREADS_WAITING_FOR_CONNECTION`, `POOL_HEALTHY`.
- Spring Boot 3.x auto-configuration via `META-INF/spring/...AutoConfiguration.imports`.
- `spring.db-pool-doctor.*` configuration properties with metadata for IDE autocomplete.
- `PoolMonitorScheduler` with a self-managed daemon thread — no `@EnableScheduling` requirement on the host application.

### Security

- Read-only design: no pool mutation, no connection mutation, no bind-value capture.
