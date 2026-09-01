# Changelog

All notable changes to Log Doctor will be documented in this file.

## [0.2.0] - 2026-09-01

### Added

- embedded local web dashboard served from the executable JAR
- structured diagnosis results for the web API
- severity, confidence, category, location, root cause, evidence and remediation fields
- browser-local history for the 10 most recent analyses
- configurable web port with `--port 9090` and `--port=9090`
- HTTP API tests and structured diagnosis unit tests
- GitHub Actions CI on Java 21
- security headers, JSON content-type validation and safer API error handling

### Changed

- the diagnosis engine now exposes structured results while preserving CLI text output
- the web frontend consumes structured JSON instead of parsing formatted diagnosis text
- CI now runs `mvn clean verify` for pushes and pull requests targeting `main`

### Security

- the web server continues to bind to `127.0.0.1`
- `/api/analyze` enforces a 5 MB payload limit
- malformed JSON returns a client error without exposing parser internals
- internal exception details are no longer returned in 500 responses
- CSP, `X-Content-Type-Options`, `X-Frame-Options` and `Referrer-Policy` headers are applied

## [0.1.0]

Initial CLI release with deterministic JVM/Spring/Hibernate/Kafka incident detection and local Ollama-assisted analysis.
