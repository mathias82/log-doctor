# Release readiness

This checklist captures the engineering gates for declaring a Log Doctor release production-ready. It is intentionally stricter than “the build is green” and should be reviewed before a major version milestone.

## Required gates

- `mvn verify` passes on Java 21.
- GitHub Actions CI is green, including Docker/Ollama E2E, GitHub Action smoke, SARIF smoke, dependency review and performance benchmark workflows.
- The diagnostic benchmark remains above its aggregate and per-category precision/recall/false-positive/exact-rule gates.
- The performance benchmark shows no unexplained regression in p50/p95/p99 latency, throughput, truncation behavior or memory observations.
- Deterministic diagnosis remains the first path; optional local LLM analysis is only used after deterministic analysis cannot resolve the failure.
- `NO_AUTOMATIC_FIX` and `automaticExecutionAllowed=false` remain authoritative safety boundaries.
- Markdown, JSON, GitHub annotation and SARIF output remain contract-tested.
- Prometheus metrics expose aggregate operational telemetry only and do not contain raw logs, prompts, evidence or LLM responses.
- Supply-chain checks remain enabled: dependency review, SBOM generation/attestation, release provenance and integrity metadata.
- Release artifacts are signed and published through the documented Maven Central workflow.
- README, API contract, supported-errors documentation and release notes match the shipped behavior.

## 1.0-specific review

Before declaring 1.0, review these compatibility commitments explicitly:

- public Java API surface and constructor compatibility
- HTTP API versioning and response-shape stability
- CLI formats, flags and exit-code stability
- GitHub Action inputs/outputs and failure-policy stability
- SARIF rule identifiers and severity mapping stability
- remediation metadata/playbook shape and safety semantics
- custom `IncidentRuleProvider` SPI loading, ordering and failure isolation
- process-local observability metric names and semantics

## Non-blocking follow-ups

The following can improve the project without being automatic blockers for a release unless a regression is observed:

- larger independently labelled real-world diagnostic corpus
- external production usage feedback
- JMH or dedicated-runner microbenchmarks for stricter performance baselines
- richer provider metadata such as explicit provider id/version/priority
- deeper observability around extension-provider failures through a decoupled metrics/event boundary

A major release should only be cut when the required gates are green and any known compatibility exception is documented in the release notes.
