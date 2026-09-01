[![CI](https://github.com/mathias82/log-doctor/actions/workflows/ci.yml/badge.svg)](https://github.com/mathias82/log-doctor/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Ollama](https://img.shields.io/badge/LLM-Ollama-orange)

# 🩺 Log Doctor

**Deterministic + local-LLM production log diagnosis for JVM / Spring / Kafka.**

Log Doctor analyzes JVM logs, groups repeated failures, builds timelines, detects spikes, scores likely failure chains, and uses deterministic diagnosis before optional local Ollama reasoning.

> **Log Doctor doesn't replace an LLM. It decides what doesn't need one.**

## Key capabilities

- deterministic incident detection before AI
- multi-incident failure-block parsing, fingerprinting and deduplication
- one optional local-LLM enrichment per unique unknown fingerprint in batch mode
- timeline, correlations, root-cause candidates and spike detection
- structured JSON and downloadable Markdown incident reports
- file upload / drag-and-drop web dashboard
- deterministic sensitive-data redaction before the LLM boundary
- explicit `NO_AUTOMATIC_FIX` safety policy for unsafe cases
- browser-local recent-analysis history without persisting raw logs server-side
- Docker Compose support for Ollama only or the complete Log Doctor + Ollama stack
- real Docker/Ollama end-to-end CI coverage

## Fastest start: full Docker stack

The repository includes a multi-stage `Dockerfile` and `compose.yml` that run **Log Doctor and Ollama together**. No local Java, Maven or Ollama installation is required once Docker is available.

Start everything:

```bash
docker compose up -d --build
```

The stack performs this startup sequence:

```text
Ollama container
      ↓ healthy
Model bootstrap container pulls qwen2.5:3b
      ↓ completed
Log Doctor container starts
      ↓
Web UI on http://localhost:8080
```

Verify the application:

```bash
curl http://localhost:8080/api/health
```

Then open:

```text
http://localhost:8080
```

Test a batch diagnosis from the host:

```bash
curl -X POST http://localhost:8080/api/analyze/batch \
  -H 'Content-Type: application/json' \
  -d '{"log":"2026-09-01 14:32:17 ERROR request failed\njava.lang.RuntimeException: unusual distributed state transition"}'
```

Stop the stack while keeping the downloaded Ollama model:

```bash
docker compose down
```

Remove the stack **and** persisted Ollama model data:

```bash
docker compose down -v
```

Use another model or host port without editing the file:

```bash
OLLAMA_MODEL=qwen2.5:0.5b docker compose up -d --build
LOG_DOCTOR_PORT=9090 docker compose up -d --build
```

Inside the Docker network, Log Doctor connects to Ollama through `http://ollama:11434`; the application container binds its web server to `0.0.0.0` so the published host port is reachable.

## Ollama-only Docker setup

If you prefer running the Java application directly on the host, use the smaller Ollama-only Compose file:

```bash
docker compose -f compose.ollama.yml up -d
curl http://localhost:11434/api/tags
```

Stop it while keeping the model:

```bash
docker compose -f compose.ollama.yml down
```

Remove persisted model data too:

```bash
docker compose -f compose.ollama.yml down -v
```

## Run Java directly

Requirements:

- JDK 21
- Maven 3.9+
- local Ollama only when exercising LLM-backed analysis

Build and run:

```bash
mvn clean verify
java -jar target/log-doctor-0.3.0.jar --web
```

By default the embedded server binds only to `127.0.0.1:8080`.

Custom port:

```bash
java -jar target/log-doctor-0.3.0.jar --web --port 9090
```

Custom bind address:

```bash
java -jar target/log-doctor-0.3.0.jar --web --host 0.0.0.0
```

The bind address can also be supplied through:

```bash
LOG_DOCTOR_BIND_ADDRESS=0.0.0.0 java -jar target/log-doctor-0.3.0.jar --web
```

The Ollama runtime can be configured with:

```text
LOG_DOCTOR_OLLAMA_URL
LOG_DOCTOR_OLLAMA_MODEL
```

Defaults remain `http://localhost:11434` and `qwen2.5:3b` outside the full Docker stack.

## Web dashboard

The file-first UI accepts `.log`, `.txt`, and `text/plain` inputs up to 5 MB. Files are read by the browser and sent as JSON for analysis; they are not persisted by the server.

Batch analysis processes up to 500 detected failure blocks and reports `truncated=true` when the cap is exceeded. Clean logs return zero detected failure blocks instead of a synthetic incident.

The dashboard shows incident grouping, severity/confidence/category, evidence, timeline, investigation order, likely correlations, scored root-cause chain candidates, spikes, raw structured JSON and a downloadable Markdown incident report.

## Analysis flow

```text
Raw Logs / Uploaded File
          ↓
Failure-block detection
          ↓
Deterministic-only DiagnosisEngine pass
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

## HTTP API

Health:

```bash
curl http://localhost:8080/api/health
```

Single analysis:

```bash
curl -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"log":"java.lang.IllegalStateException: transition not allowed"}'
```

Batch analysis:

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

The repository contains two Docker-backed integration paths:

```bash
LOG_DOCTOR_OLLAMA_MODEL=qwen2.5:0.5b mvn -Pollama-it verify
```

and the full Compose stack workflow in `.github/workflows/docker-stack-e2e.yml`, which builds the application image, starts Ollama, pulls a lightweight model, checks `/api/health`, sends an unknown failure through `/api/analyze/batch`, and verifies `llmUsed=true`.

## Maven Central

Coordinates:

```xml
<dependency>
    <groupId>io.github.mathias82</groupId>
    <artifactId>log-doctor</artifactId>
    <version>0.3.0</version>
</dependency>
```

The artifact becomes resolvable only after the corresponding version is successfully published. Release publication uses the Sonatype Central Publisher Portal, sources/Javadocs, GPG signing and `.github/workflows/publish-maven-central.yml`.

Required repository secrets for publishing are `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`. Maven Central releases are immutable; never reuse a published version.

## Supported incidents

Representative deterministic rules include Hibernate `LazyInitializationException`, Spring bean/profile/configuration problems, Jackson/JSON deserialization failures, Kafka topic/schema failures, HikariCP timeouts, deadlocks/thread starvation, `OutOfMemoryError`, GC thrashing and common Java runtime exceptions.

See `docs/supported-errors.md` and `docs/incidents.md` for the current rule catalog.

## Release notes

See `CHANGELOG.md` and `docs/release-notes-0.3.0.md`.

## Philosophy

- Determinism before AI
- Safety before automation
- Local-first, privacy-first
- Evidence before causation claims
- Production realism over demos

## License

Apache 2.0 License — use it, extend it, improve it.
