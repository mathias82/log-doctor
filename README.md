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
- Spring Boot startup failure-analysis extraction with `Description` / `Action` guidance
- deterministic nested exception cause-chain extraction
- explicit `WHY MATCHED` explanations for deterministic diagnoses
- auditable 0-100 deterministic match-strength scoring
- multi-incident failure-block parsing, fingerprinting and deduplication
- one optional local-LLM enrichment per unique unknown fingerprint in batch mode
- timeline, correlations, root-cause candidates and spike detection
- structured JSON and downloadable Markdown incident reports
- file upload / drag-and-drop web dashboard
- visible deterministic/Ollama provenance and human-review state in the web UI
- deterministic sensitive-data redaction before the LLM boundary
- explicit `NO_AUTOMATIC_FIX` safety policy for unsafe cases
- browser-local recent-analysis history without persisting raw logs server-side
- Docker Compose support for Ollama only or the complete Log Doctor + Ollama stack
- real Docker/Ollama end-to-end CI coverage

## Fastest start: full Docker stack

The repository includes a multi-stage `Dockerfile` and `compose.yml` that run **Log Doctor and Ollama together**. No local Java, Maven or Ollama installation is required once Docker is available.

```bash
docker compose up -d --build
```

Startup flow:

```text
Ollama container
      ↓ healthy
Model bootstrap container pulls qwen2.5:3b
      ↓ completed
Log Doctor container starts
      ↓
Web UI on http://localhost:8080
```

Verify and open:

```bash
curl http://localhost:8080/api/health
```

```text
http://localhost:8080
```

Test batch diagnosis:

```bash
curl -X POST http://localhost:8080/api/analyze/batch \
  -H 'Content-Type: application/json' \
  -d '{"log":"2026-09-01 14:32:17 ERROR request failed\njava.lang.RuntimeException: unusual distributed state transition"}'
```

Stop while keeping the model:

```bash
docker compose down
```

Remove persisted model data too:

```bash
docker compose down -v
```

Overrides:

```bash
OLLAMA_MODEL=qwen2.5:0.5b docker compose up -d --build
LOG_DOCTOR_PORT=9090 docker compose up -d --build
```

Inside Docker, Log Doctor connects to `http://ollama:11434` and binds to `0.0.0.0` so the published port is reachable.

## Ollama-only Docker setup

```bash
docker compose -f compose.ollama.yml up -d
curl http://localhost:11434/api/tags
```

```bash
docker compose -f compose.ollama.yml down
```

Use `down -v` to remove persisted model data.

## Run Java directly

Requirements: JDK 21, Maven 3.9+, and local Ollama only for LLM-backed analysis.

```bash
mvn clean verify
java -jar target/log-doctor-0.4.2.jar --web
```

Default bind: `127.0.0.1:8080`.

```bash
java -jar target/log-doctor-0.4.2.jar --web --port 9090
java -jar target/log-doctor-0.4.2.jar --web --host 0.0.0.0
LOG_DOCTOR_BIND_ADDRESS=0.0.0.0 java -jar target/log-doctor-0.4.2.jar --web
```

Ollama configuration:

```text
LOG_DOCTOR_OLLAMA_URL
LOG_DOCTOR_OLLAMA_MODEL
```

Defaults outside the full Docker stack remain `http://localhost:11434` and `qwen2.5:3b`.

## Web dashboard

The file-first UI accepts `.log`, `.txt`, and `text/plain` inputs up to 5 MB. Files are read by the browser and sent as JSON for analysis; they are not persisted by the server.

Batch analysis processes up to 500 detected failure blocks and reports `truncated=true` when the cap is exceeded. Clean logs return zero detected failure blocks instead of a synthetic incident.

The dashboard shows incident grouping, severity/confidence/category, deterministic-vs-Ollama provenance, human-review state, evidence, fix policy, timeline, investigation order, likely correlations, scored root-cause chain candidates, spikes, raw structured JSON and a downloadable Markdown incident report.

![Incident diagnostics detail](docs/images/incident-detail-preview.svg)

## Analysis flow

```text
Raw Logs / Uploaded File
          ↓
Failure-block detection
          ↓
Cause-chain extraction
          ↓
Deterministic-only DiagnosisEngine pass
          ↓
Rule match explanation + match-strength score
          ↓
Fingerprint + deduplicate
          ↓
Optional LLM enrichment per unique unknown group
          ↓
Timeline + correlations
          ↓
Root-cause scoring + spike detection
          ↓
Structured JSON + Web UI + Markdown report
```

Correlation and root-cause scoring are heuristic evidence and do **not** prove causation.

## Cause chains and why a rule matched

For JVM stack traces, Log Doctor extracts visible nested exception chains in outer-to-deepest order. Structured API results include `causeChain`, with the line number, exception type, message and source evidence for each cause. Unknown failures use the deepest visible cause as their root-cause evidence.

Deterministic diagnoses also expose `matchReasons`. The text diagnosis renders the same information under `WHY MATCHED`, including the exact rule class that matched and the rule evidence when available. This makes a deterministic decision auditable instead of returning only a final label.

Example:

```text
CAUSE CHAIN:
- line 1: org.springframework.beans.factory.BeanCreationException: failed to create bean
- line 4: java.net.ConnectException: Connection refused

WHY MATCHED:
- Matched deterministic rule CommonFailureCatalogRule
- Matching evidence: org.springframework.beans.factory.BeanCreationException: failed to create bean
```

## Spring Boot startup diagnostics

When Spring Boot emits its standard `APPLICATION FAILED TO START` failure-analysis banner, Log Doctor treats that report as structured deterministic evidence instead of reducing it to a generic wrapper failure. It extracts `Description:` as root-cause guidance and `Action:` as remediation. When those sections are missing, it falls back to the deepest visible `Caused by:` line.

Example:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:
Parameter 0 of constructor in com.acme.OrderService required a bean of type
'com.acme.PaymentClient' that could not be found.

Action:
Consider defining a bean of type 'com.acme.PaymentClient' in your configuration.
```

This becomes a deterministic `SPRING_BOOT_STARTUP_FAILURE` diagnosis with Spring configuration remediation and no Ollama dependency for the startup explanation itself. More specific Spring rules still run first and retain precedence.

## Deterministic match scoring

Known deterministic diagnoses now include an evidence-strength score. This is **not a probability** and does not estimate the chance that a diagnosis is correct. It is an auditable 0-100 score derived from deterministic signals such as a rule match, extracted evidence, a matching exception in the visible cause chain, and whether a specialized rule matched before the broad catalog.

Structured API fields:

```text
matchScore
matchConfidence
matchScoreFactors
```

Confidence bands are `LOW`, `MEDIUM`, `HIGH`, and `VERY_HIGH`. Unknown failures use score `0` / `NONE` because no deterministic rule matched. Protected concurrency and business-invariant fallbacks remain high-confidence human-review paths and do not become automatically fixable because of a high score.

Example text output:

```text
MATCH SCORE: 100/100 (VERY_HIGH)
- Deterministic rule matched (+55)
- Matching evidence was extracted (+20)
- Visible cause chain supports the matched incident (+15)
- Specialized rule matched before the broad catalog (+10)
```

## HTTP API

```bash
curl http://localhost:8080/api/health
```

```bash
curl -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"log":"java.lang.IllegalStateException: transition not allowed"}'
```

```bash
curl -X POST http://localhost:8080/api/analyze/batch \
  -H 'Content-Type: application/json' \
  -d '{"log":"2026-09-01 14:32:17 ERROR request failed\njava.lang.RuntimeException: boom"}'
```

The API accepts JSON only and enforces a 5 MB decoded-log limit.

## Sensitive-data redaction

Before context reaches the local LLM boundary, Log Doctor performs deterministic best-effort redaction for common bearer tokens/JWTs, passwords, secrets, API keys, access/refresh tokens, sensitive query parameters, email addresses and IPv4 addresses.

Redaction is defense in depth, not a guarantee that every possible secret format will be detected.

## Real Ollama integration tests

```bash
LOG_DOCTOR_OLLAMA_MODEL=qwen2.5:0.5b mvn -Pollama-it verify
```

The full Compose E2E workflow in `.github/workflows/docker-stack-e2e.yml` builds the application image, starts Ollama, pulls a lightweight model, checks `/api/health`, sends an unknown failure through `/api/analyze/batch`, verifies `llmUsed=true`, and tears the stack down.

## Maven Central

Coordinates for this release:

```xml
<dependency>
    <groupId>io.github.mathias82</groupId>
    <artifactId>log-doctor</artifactId>
    <version>0.4.2</version>
</dependency>
```

The artifact becomes resolvable only after the corresponding version is successfully published. Publication uses the Sonatype Central Publisher Portal, sources/Javadocs, GPG signing and `.github/workflows/publish-maven-central.yml`.

Required repository secrets are `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY`, and `MAVEN_GPG_PASSPHRASE`. Maven Central releases are immutable; never reuse a published version.

## Supported incidents

Specialized deterministic rules handle high-fidelity cases first. A broad catalog then covers 80+ common Java/JVM, Spring, Hibernate/JPA, JDBC/Hikari, Kafka and Schema Registry failures before unknown incidents reach optional local Ollama reasoning. This includes JVM linkage/classpath failures, Spring bean, startup and transaction failures, Hibernate/JPA locking/mapping/JDBC failures, Kafka auth/ACL/replication/consumer/transaction errors, and Schema Registry failures.

See `docs/supported-errors.md` and `docs/incidents.md` for the current rule catalog.

## Release notes

See `CHANGELOG.md` and `docs/release-notes-0.4.0.md`.

## Philosophy

- Determinism before AI
- Safety before automation
- Local-first, privacy-first