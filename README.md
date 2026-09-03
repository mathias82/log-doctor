[![CI](https://github.com/mathias82/log-doctor/actions/workflows/ci.yml/badge.svg)](https://github.com/mathias82/log-doctor/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Ollama](https://img.shields.io/badge/LLM-Ollama-orange)

# 🩺 Log Doctor

**Deterministic + local-LLM production log diagnosis for JVM / Spring / Kafka.**

Log Doctor analyzes JVM logs, groups repeated failures, builds timelines, detects spikes, scores likely failure chains, and uses deterministic diagnosis before optional local Ollama reasoning.

> **Log Doctor doesn't replace an LLM. It decides what doesn't need one.**

![Log Doctor dashboard](docs/images/dashboard-overview.svg)

## Key capabilities

- deterministic incident detection before AI
- broad curated Java/JVM, Spring, Hibernate/JPA, JDBC/Hikari, Kafka and Schema Registry error catalog
- 120-case labelled diagnostic regression corpus with aggregate and per-category JVM/Spring/Kafka/DB quality gates
- synthetic performance/load benchmark with p50/p95/p99 latency, throughput, approximate heap delta and 500-block safety-cap coverage
- pluggable deterministic rule providers through Java `ServiceLoader`, with fail-soft isolation
- Spring Boot startup failure-analysis extraction with `Description` / `Action` guidance
- deterministic nested exception cause-chain extraction, `WHY MATCHED` explanations and auditable match-strength scoring
- stack-trace-aware grouping and structured grouping metadata
- versioned HTTP API contract signal
- CI-friendly JSON, GitHub annotations, SARIF 2.1.0 and GitHub Code Scanning integration
- official composite GitHub Action with severity-aware failure policies and stable CI exit codes
- privacy-safe request and incident observability, Prometheus latency histogram, error counters and local-LLM usage metrics
- Prometheus scrape endpoint plus OpenTelemetry Collector bridge configuration
- full specialized Kafka operational diagnostic matrix with Schema Registry context guards
- release provenance, CycloneDX SBOM attestation, SHA-256 integrity metadata and pull-request dependency review
- structured remediation metadata and investigation-first playbooks with automatic execution disabled
- Markdown remediation rendering for Inspect evidence, Change candidates, Validate recovery and Escalate when phases
- multi-incident parsing, fingerprinting, deduplication, timelines, correlations, root-cause candidates and spike detection
- optional local-Ollama enrichment only after deterministic analysis
- structured JSON and downloadable Markdown incident reports
- file upload / drag-and-drop web dashboard
- deterministic sensitive-data redaction before the LLM boundary
- Docker Compose support and real Docker/Ollama end-to-end CI coverage

## Fastest start: full Docker stack

```bash
docker compose up -d --build
```

Web UI: `http://localhost:8080`. Health: `curl http://localhost:8080/api/health`. Prometheus metrics: `curl http://localhost:8080/metrics`.

## Run Java directly

Requirements: JDK 21, Maven 3.9+, and local Ollama only for LLM-backed analysis.

```bash
mvn clean verify
java -jar target/log-doctor-0.4.2.jar --web
```

Default bind: `127.0.0.1:8080`.

## API and dashboard

`POST /api/analyze` returns one structured diagnosis. `POST /api/analyze/batch` returns grouped incidents and advanced batch insights. Every HTTP response includes `X-Log-Doctor-Api-Version: 1`. The dashboard renders backend-owned grouping, match evidence, remediation guardrails and playbooks without duplicating policy logic client-side. Automatic remediation execution remains disabled.

## Runtime observability

The embedded server exposes aggregate process-local metrics at `GET /api/metrics` as JSON and `GET /metrics` in Prometheus text format. Request volume and returned incident volume are tracked separately. Batch requests containing only unknown incidents are classified as unknown rather than deterministic diagnoses.

Prometheus includes a `log_doctor_analysis_latency_milliseconds` histogram with fixed buckets plus sum/count, while the existing average/max gauges remain available for compatibility. The surface also includes analysis-error and rule-provider-failure counters. Metrics contain no raw logs, evidence, prompts or LLM responses. `observability/otel-collector-config.yaml` provides a vendor-neutral OpenTelemetry Collector bridge. See [docs/runtime-observability.md](docs/runtime-observability.md).

## Diagnostic and performance quality

`DiagnosticBenchmarkTest` evaluates a checked-in 120-case labelled JVM/Spring/Kafka/DB corpus with aggregate and per-category precision, recall, false-positive and exact-rule gates. `PerformanceBenchmarkTest` exercises synthetic workloads at 50, 200, 500 and 750 incident blocks plus an approximately 2 MiB log and reports average/p50/p95/p99 latency, throughput, truncation and approximate heap delta. These are reproducible regression signals, not production-wide accuracy or SLA claims. See [docs/diagnostic-benchmark.md](docs/diagnostic-benchmark.md) and [docs/performance-benchmark.md](docs/performance-benchmark.md).

## CI, GitHub Actions and SARIF

The CLI supports `text`, `json`, `github` and `sarif` output plus `--fail-on none|diagnosis|high|critical`. Stable exit codes distinguish success, policy-triggered findings and usage/analysis errors. The repository includes an official composite GitHub Action and SARIF Code Scanning smoke coverage. See [docs/ci-github-integration.md](docs/ci-github-integration.md) and [docs/sarif-code-scanning.md](docs/sarif-code-scanning.md).

## Remediation reporting

Remediation guidance is backend-owned and investigation-first. Markdown rendering preserves the same safety metadata and presents playbooks in four explicit phases: **Inspect evidence**, **Change candidates**, **Validate recovery**, and **Escalate when**. These steps are guidance only; they never authorize or execute a change.

## Safety

Log Doctor is investigation-first. Match confidence is evidence strength, not execution authority. `NO_AUTOMATIC_FIX` remains authoritative and remediation metadata keeps `automaticExecutionAllowed=false`.

## Documentation

Detailed documentation lives under [`docs/`](docs/), including supported incidents, Kafka diagnostics, custom rule providers, API contract, observability, benchmarks, CI/SARIF integration, supply-chain security and release integrity.
