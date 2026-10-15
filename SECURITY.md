# Security Policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |

## Reporting a vulnerability

Please report security issues privately to **adeshsuryan2005@gmail.com** with the subject `spring-db-pool-doctor security`. Do not open a public issue for a vulnerability until a fix is released.

## Security posture of this library

- Read-only: the library never modifies pool settings, never closes application-owned connections, and never executes writes of any kind.
- No bind values, SQL text, connection URLs, credentials, or request IDs are captured.
- Stack-trace sampling is opt-in and rate-limited.
- Instrumented mode wraps `DataSource`/`Connection` but fails open: an instrumentation exception never blocks or delays the real database call.
- The Actuator endpoint (when implemented) is disabled by default and must be secured by the host application before exposure.
