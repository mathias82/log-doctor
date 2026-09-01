# Changelog

All notable changes to Log Doctor will be documented in this file.

## [0.4.1] - 2026-09-01

### Changed

- Maven Central publishing now uses the same GitHub Actions secret names as `spring-kafka-contract-starter`: `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY`, and `MAVEN_GPG_PASSPHRASE`
- GPG signing uses non-interactive batch/loopback arguments compatible with GitHub Actions
- release documentation and Maven coordinates now target `0.4.1`

### Fixed

- fixes the failed `0.4.0` publishing attempt where Log Doctor expected different secret names and therefore received empty Maven/GPG credentials
- fixes release version/tag alignment so the Maven Central workflow can verify `v0.4.1` against `pom.xml` version `0.4.1`

## [0.4.0] - 2026-09-01

### Added

- real Docker-backed Ollama integration testing for unknown incident analysis
- full Docker Compose stack for Log Doctor, Ollama and automatic model bootstrap
- multi-stage Java 21 application Docker image
- full-stack GitHub Actions E2E workflow that builds the image, starts the stack, checks application health and verifies a real unknown failure returns `llmUsed=true`
- configurable Ollama URL and model for local, CI and container environments
- configurable web bind address through `--host` and `LOG_DOCTOR_BIND_ADDRESS`
- browser utility tests for escaping, severity mapping, upload validation and bounded privacy-safe history
- regression coverage for the 500 failure-block cap, API serialization, structured log-level parsing and timeline ordering

### Changed

- batch analysis now performs deterministic grouping before optional LLM enrichment, limiting model use to at most one call per unique unknown incident fingerprint
- clean INFO/DEBUG/WARN logs no longer create a synthetic failure block
- `WARNING` is normalized to `WARN` by structured log-level parsing
- failure parsing avoids treating log-level words embedded in message text as actual levels
- timeline first/last occurrence handling is chronological and avoids unsafe comparisons between incompatible timestamp bases
- request envelope sizing now permits valid 5 MB decoded logs even when JSON escaping expands the HTTP body
- batch API exposes `uniqueIncidents` explicitly in serialized JSON
- README now documents the one-command Docker stack as the fastest startup path
- executable artifact version is now `0.4.0`

### Security

- the 5 MB decoded-log limit remains authoritative even with the larger JSON request envelope allowance
- direct Java web execution remains bound to `127.0.0.1` by default; `0.0.0.0` is enabled explicitly for the Docker application container
- raw logs and evidence remain excluded from browser history persistence
- deterministic redaction remains in front of the local LLM boundary

## [0.3.0] - 2026-09-01

### Added

- multi-incident log analysis with deterministic failure-block detection
- incident fingerprinting and grouping for repeated failures
- per-incident first/last occurrence timeline metadata
- timestamp-aware likely incident correlations
- root-cause chain candidate scoring with LOW / MEDIUM / HIGH confidence labels
- per-minute spike detection with peak counts and baseline multipliers
- structured Markdown incident reports suitable for investigations and postmortems
- Web UI download action for generated incident reports
- prompt-boundary redaction for bearer tokens, JWTs, passwords, API keys, tokens, email addresses and IPv4 addresses
- batch-analysis metadata for detected blocks and truncation when the 500-block safety cap is reached
- Maven Central publishing profile with source JARs, Javadoc JARs, GPG signing and Sonatype Central Publisher Portal integration
- tag-triggered GitHub Actions workflow for signed Maven Central releases

### Changed

- uploaded log files are analyzed through `/api/analyze/batch` and rendered as grouped incident dashboards
- stack-trace parsing keeps nested `Caused by:` sections with the parent failure
- timestamped INFO/WARN records terminate active failure blocks instead of contaminating incident evidence
- correlation compares offset timestamps by instant and avoids inferred chains when timestamps are absent
- incident fingerprints normalize volatile IDs including numbers, hex values and UUIDs
- Ollama failures now fall back safely instead of being treated as successful LLM diagnoses
- the executable artifact version is now `0.3.0`
- release metadata now includes license, developer and SCM information required for Maven Central publication

### Security

- sensitive values are redacted before log context reaches the local LLM boundary
- JSON-style secret assignments and secret query parameters are covered by deterministic redaction
- IPv4 redaction validates address ranges before replacement
- Maven Central credentials and GPG material are supplied only through GitHub Actions secrets
- the local HTTP server remains bound to `127.0.0.1` by default and retains request-size and content-type validation

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
