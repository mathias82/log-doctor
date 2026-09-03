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
- measurable diagnostic regression benchmark with precision, recall, false-positive rate and exact-rule accuracy gates
- hard-negative regression cases for cross-system lookalikes such as generic HTTP failures versus Schema Registry errors
- pluggable deterministic rule providers through Java `ServiceLoader`, with built-in precedence preserved
- fail-soft isolation for third-party rule/provider failures so broken extensions cannot take down core diagnosis
- Spring Boot startup failure-analysis extraction with `Description` / `Action` guidance
- deterministic nested exception cause-chain extraction
- explicit `WHY MATCHED` explanations and auditable 0-100 match-strength scoring
- stack-trace-aware grouping with deepest-cause frame association, module/native frame support and dashboard grouping explainability
- structured grouping metadata in grouped API responses so clients do not parse opaque fingerprint delimiters
- versioned HTTP API contract signal for CI/CD, monitoring and external integrations
- CI-friendly CLI JSON and GitHub Actions annotation output modes
- privacy-safe runtime observability for analysis volume, deterministic/unknown outcomes, local LLM usage, failures and latency
- Prometheus scrape endpoint plus OpenTelemetry Collector bridge configuration
- full specialized Kafka operational diagnostic matrix with Schema Registry context guards and negative cases
- release provenance, CycloneDX SBOM attestation, SHA-256 integrity metadata and pull-request dependency review
- structured remediation metadata returned by both single and grouped diagnosis APIs
- remediation safety state, allowed action types and incident-aware verification steps
- typed remediation profiles centralize incident-to-guidance routing so playbooks and verification cannot drift independently
- structured investigation-first remediation playbooks with inspect, change-candidate, validation and escalation phases
- contextual verification guidance for Kafka, Spring Boot startup, HikariCP saturation and JVM OutOfMemory failures
- Markdown reports include remediation safety, allowed actions and verification steps
- dashboard renders backend-owned grouping/remediation metadata and operational playbooks without duplicating policy logic client-side
- explicit `NO_AUTOMATIC_FIX` policy and disabled automatic execution
- multi-incident parsing, fingerprinting, deduplication, timelines, correlations, root-cause candidates and spike detection
- optional local-Ollama enrichment only after deterministic analysis
- structured JSON and downloadable Markdown incident reports
- file upload / drag-and-drop web dashboard
- deterministic sensitive-data redaction before the LLM boundary
- Docker Compose support for Ollama only or the complete Log Doctor + Ollama stack
- real Docker/Ollama end-to-end CI coverage

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

## Web dashboard

The file-first UI accepts `.log`, `.txt`, and `text/plain` inputs up to 5 MB. Files are read by the browser and sent as JSON for analysis; they are not persisted by the server.

Each grouped incident returned by the batch API includes a structured `grouping` object with `strategy`, `exceptionType`, `frames`, and `lineNumbersIgnored`. The dashboard consumes that object directly and no longer reverse-parses the opaque `fingerprint` value. The fingerprint remains available as a stable internal/backward-compatible identity.

Each diagnosis carries the backend-owned `remediation` object when a failure is present. It contains `safety`, `allowedActions`, `verificationSteps`, `automaticExecutionAllowed`, and a structured `playbook`. The dashboard renders the four playbook phases directly from the backend contract as `Inspect evidence`, `Change candidates`, `Validate recovery`, and `Escalate when`. It does not infer remediation policy in JavaScript.

For known deterministic incidents, remediation can be more specific than the broad category. Kafka authorization/replication/consumer/schema/producer cases, Spring Boot startup failures, HikariCP connection-pool exhaustion and JVM OutOfMemory failures receive targeted checks and playbooks while the existing fix policy remains authoritative. These specializations are selected through a typed `RemediationProfile`, so verification guidance and playbook selection share one central routing decision instead of duplicating incident-type string checks.

Unknown, infrastructure, business and other protected cases remain human-review-only. Automatic execution is currently always `false`.

The dashboard shows match evidence, grouping signature, cause chain, remediation guardrails and playbooks, provenance, timeline, investigation order, correlations, root-cause candidates and spikes.

Stack-aware grouping associates the selected deepest visible exception/error/throwable with its own first frames, ignores source line numbers, handles module-qualified frames plus `Native Method` / `Unknown Source`, and ignores suppressed failures when selecting the grouping cause. See [docs/stack-trace-fingerprinting.md](docs/stack-trace-fingerprinting.md).

## API

`POST /api/analyze` returns one structured diagnosis. `POST /api/analyze/batch` returns grouped incidents and advanced batch insights. Both accept JSON with a `log` string.

Every HTTP response includes `X-Log-Doctor-Api-Version: 1`, and `/api/health` also exposes `apiVersion`. Integrations can validate this contract version before processing a response without changing the existing JSON payload shapes. See [docs/api-contract.md](docs/api-contract.md).

Example grouping fragment on a grouped incident:

```json
{
  "grouping": {
    "strategy": "STACK_TRACE",
    "exceptionType": "java.lang.nullpointerexception",
    "frames": ["com.acme.orderservice.load(orderservice.java)"],
    "lineNumbersIgnored": true
  }
}
```

Example remediation fragment present on a single diagnosis or grouped incident:

```json
{
  "remediation": {
    "safety": "REVIEW_BEFORE_APPLY",
    "allowedActions": ["SPRING_CONFIG"],
    "verificationSteps": ["Validate the effective runtime configuration"],
    "automaticExecutionAllowed": false,
    "playbook": {
      "inspect": ["FailureAnalysis Description and Action"],
      "changeCandidates": ["Correct the identified configuration/dependency mismatch"],
      "validate": ["Start with the same profile and environment"],
      "escalationSignals": ["Configuration source or secret ownership is unclear"]
    }
  }
}
```

Match confidence is evidence strength only. It never grants execution permission and never overrides `NO_AUTOMATIC_FIX`.

## Runtime observability

The embedded web server exposes aggregate process-local operational metrics at `GET /api/metrics` as JSON and `GET /metrics` in Prometheus text exposition format. The metrics surface tracks completed analyses, deterministic and unknown outcomes, no-failure results, local LLM usage, analysis errors, and average/maximum analysis latency.

The endpoint intentionally excludes raw logs, evidence, prompts, exception messages and LLM responses. `observability/otel-collector-config.yaml` provides an OpenTelemetry Collector bridge that scrapes the Prometheus endpoint, keeping the application vendor-neutral while allowing an OTLP-compatible exporter to be configured in the collector. See [docs/runtime-observability.md](docs/runtime-observability.md).

## Diagnostic benchmark

`DiagnosticBenchmarkTest` evaluates the checked-in curated corpus and publishes measurable precision, recall, false-positive rate and exact-rule accuracy. The current regression gates require precision >= 95%, recall >= 90%, false-positive rate <= 5% and exact-rule accuracy >= 90%.

The corpus includes hard negatives that deliberately resemble supported failures but come from a different subsystem. Schema Registry diagnosis, for example, requires explicit Schema Registry/Confluent context or a subject path; a generic Spring `RestClientException` with HTTP `401` or `409` is not sufficient.

The generated `target/diagnostic-benchmark.json` is added to the GitHub Actions job summary and uploaded as a CI artifact. These numbers are regression metrics for the curated corpus, not a claim of production-wide statistical accuracy. See [docs/diagnostic-benchmark.md](docs/diagnostic-benchmark.md).

## CI and GitHub output

The CLI keeps human-readable text as the default and adds machine-friendly formats:

```bash
java -jar target/log-doctor-0.4.2.jar --file application.log --format json
java -jar target/log-doctor-0.4.2.jar --file application.log --format github
```

`json` emits the structured diagnosis contract. `github` emits escaped GitHub Actions workflow annotations with the source file and failure line when available. Neither mode executes remediation actions. See [docs/ci-github-integration.md](docs/ci-github-integration.md).

## Kafka diagnostic quality

`KafkaOperationalFailureRuleTest` covers all specialized Kafka operational incident types, including authorization, authentication, producer state, replication, consumer state, message sizing, metadata, and Schema Registry failures. Schema Registry authorization/compatibility matches require Schema Registry context so generic `401 Unauthorized`, unrelated Spring REST-client `401`/`409` responses, or ordinary `incompatible schema` text are not misclassified. See [docs/kafka-diagnostic-quality-matrix.md](docs/kafka-diagnostic-quality-matrix.md).

## Custom deterministic rules

`IncidentRuleProvider` lets an application or separate JAR add deterministic rules without changing Log Doctor core. Providers are discovered through Java `ServiceLoader` and their rules run after specialized built-ins but before `CommonFailureCatalogRule`, so custom diagnostics can refine generic catalog matches without silently replacing higher-fidelity built-in rules.

Extension code is isolated at the SPI boundary: provider discovery/rule-list failures are logged and skipped, and a custom rule that throws or returns `null` is treated as no match so diagnosis can continue to the next extension or built-in catalog rule. Core built-in failures remain visible rather than being swallowed.

Embedded applications can also construct `IncidentDetector` with an explicit `List<IncidentRule>` instead of classpath discovery. Custom rules remain subject to the same downstream fix-policy and remediation-safety contracts; they do not enable automatic execution.

See [docs/custom-rule-providers.md](docs/custom-rule-providers.md).

## Rule quality matrix

`DeterministicRuleQualityMatrixTest` protects specialized-rule precedence and representative benign negatives. See [docs/rule-quality-matrix.md](docs/rule-quality-matrix.md).

## Supply-chain security

Tag-triggered releases carry GitHub build provenance plus a CycloneDX JSON SBOM attestation. Release integrity artifacts include `SHA256SUMS` and the generated SBOM. Pull requests targeting `main` also run dependency review and fail on newly introduced high/critical vulnerabilities or denied GPL-3.0/AGPL-3.0 licenses.

Consumers can verify a downloaded release JAR with `gh attestation verify <jar> --repo mathias82/log-doctor`. See [docs/supply-chain-security.md](docs/supply-chain-security.md) for the complete verification model and limitations.

## Maven Central

```xml
<dependency>
  <groupId>io.github.mathias82</groupId>
  <artifactId>log-doctor</artifactId>
  <version>0.4.2</version>
</dependency>
```

## Release notes

See [CHANGELOG.md](CHANGELOG.md) and [docs/release-notes-0.4.0.md](docs/release-notes-0.4.0.md).
