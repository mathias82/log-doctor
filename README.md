[![CI](https://github.com/mathias82/log-doctor/actions/workflows/ci.yml/badge.svg)](https://github.com/mathias82/log-doctor/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Ollama](https://img.shields.io/badge/LLM-Ollama-orange)

# 🩺 Log Doctor

**Deterministic + local-LLM production log diagnosis for JVM / Spring / Kafka.**

Log Doctor analyzes JVM logs, locates the most relevant failure, applies deterministic incident rules first, and uses a local Ollama model only when a safe deterministic diagnosis is unavailable.

<p align="center">
  <img src="https://cdn.simpleicons.org/openjdk/437291" alt="OpenJDK" width="52" height="52" />
  &nbsp;&nbsp;&nbsp;
  <img src="https://cdn.simpleicons.org/apachemaven/C71A36" alt="Apache Maven" width="52" height="52" />
  &nbsp;&nbsp;&nbsp;
  <img src="https://cdn.simpleicons.org/spring/6DB33F" alt="Spring" width="52" height="52" />
  &nbsp;&nbsp;&nbsp;
  <img src="https://cdn.simpleicons.org/apachekafka/777777" alt="Apache Kafka" width="52" height="52" />
  &nbsp;&nbsp;&nbsp;
  <img src="https://cdn.simpleicons.org/ollama/777777" alt="Ollama" width="52" height="52" />
</p>

<p align="center"><sub>Java 21 · Maven · Spring ecosystem · Apache Kafka · local Ollama</sub></p>

## Why Log Doctor?

Production logs are noisy, root causes are often buried in nested exceptions, and generic AI advice is risky. Log Doctor is designed around four principles:

- deterministic rules before AI
- safe remediation policies before automatic fixes
- local-first analysis with no cloud log upload
- structured evidence instead of opaque recommendations

## Key capabilities

- deterministic detection for common Spring Boot, Hibernate, Kafka, JDBC, memory and threading failures
- structured diagnosis with severity, confidence, category, root cause, location and evidence
- policy-driven fix types and explicit human-review decisions
- local Ollama assistance for unknown or ambiguous failures
- CLI mode for terminal workflows
- embedded web dashboard in the same executable JAR
- browser-local recent-analysis history; raw logs are not persisted by the server
- hardened local HTTP API with payload limits, JSON validation and security headers
- Java 21 CI with unit and HTTP API tests

## Web dashboard

Build the project and start the embedded dashboard:

```bash
mvn clean verify
java -jar target/log-doctor-0.2.0.jar --web
```

Then open:

```text
http://localhost:8080
```

Use a custom port with either form:

```bash
java -jar target/log-doctor-0.2.0.jar --web --port 9090
java -jar target/log-doctor-0.2.0.jar --web --port=9090
```

### Dashboard preview

<p align="center">
  <img src="https://github.com/user-attachments/assets/8b0f5d3c-2519-4927-85e7-c26962370dfa" alt="Log Doctor dashboard preview" width="900" />
</p>

The dashboard shows:

- severity and confidence
- incident category and diagnosis mode
- summary and root cause
- failure location and source line when available
- remediation and allowed fix type
- human-review requirement
- expandable evidence and raw diagnosis
- the 10 most recent analyses stored only in browser `localStorage`

## CLI usage

Analyze a log file directly:

```bash
java -jar target/log-doctor-0.2.0.jar --file examples/app.log
```

The existing CLI output remains supported while the web API uses the structured diagnosis model.

## Local Ollama setup

Install Ollama, pull a model, and start the local service:

```bash
ollama pull llama3
ollama serve
```

Ollama normally listens on:

```text
http://localhost:11434
```

Log Doctor keeps log analysis local and does not require a cloud LLM API.

## Analysis flow

```text
Raw Logs
   ↓
LogParser
   ↓
FailureLocator
   ↓
FailureContextExtractor
   ↓
IncidentDetector
   ↓
┌────────────────────────┬───────────────────────────┐
│ Deterministic incident │ Unknown / ambiguous      │
│ HIGH confidence        │ failure                   │
│ policy-constrained fix │ local Ollama explanation │
└────────────────────────┴───────────────────────────┘
   ↓
Structured DiagnosisResult
   ↓
CLI text / JSON API / Web dashboard
```

## Fix safety

Every deterministic incident is constrained by `FixPolicy`. Some classes of failure intentionally result in `NO_AUTOMATIC_FIX` and require human investigation. Examples include concurrency conflicts, data-consistency failures and unsafe threading scenarios.

```text
No safe automatic fix, human investigation required.
```

Refusing to guess is part of the product behavior.

## Example: Hibernate LazyInitializationException

Input:

```text
Caused by: org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role:
com.mycompany.myservice.domain.User.orders, could not initialize proxy - no Session
    at com.mycompany.myservice.service.UserService.toDto(UserService.java:74)
```

A deterministic rule can classify the incident, identify the service-layer location, attach supporting evidence and constrain the remediation to an allowed Java-code fix.

## HTTP API

Health check:

```bash
curl http://localhost:8080/api/health
```

Analyze a log:

```bash
curl -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"log":"java.lang.IllegalStateException: transition not allowed"}'
```

`/api/analyze` accepts JSON only and enforces a 5 MB log-payload limit. The embedded server binds to `127.0.0.1` by default.

## Build and test

Requirements:

- JDK 21
- Maven 3.9+
- Ollama only when exercising LLM-backed analysis

Run the full verification suite:

```bash
mvn clean verify
```

GitHub Actions runs the same verification flow for pushes and pull requests targeting `main`.

## Supported incidents

Representative deterministic rules include:

- Hibernate `LazyInitializationException`
- Spring `NoSuchBeanDefinitionException`
- Spring profile/configuration mismatches
- Jackson / JSON deserialization failures
- Kafka topic and schema failures
- HikariCP timeouts
- deadlocks and thread starvation
- `OutOfMemoryError`
- GC thrashing

See [Supported Errors](docs/supported-errors.md) and [Detailed Incident Breakdown](docs/incidents.md) for the current rule catalog.

## Project structure

```text
src/main/java/io/github/mathias82/logdoctor/
├── cli/
├── core/
├── engine/
├── llm/
├── rules/
└── web/

src/main/resources/web/
├── index.html
├── app.css
└── app.js
```

## Release notes

See [CHANGELOG.md](CHANGELOG.md) for version history. The `0.2.0` line introduces the embedded web dashboard, structured diagnosis API, local browser history, configurable web port, CI coverage and HTTP hardening.

## Philosophy

- Determinism before AI
- Safety before automation
- Local-first, privacy-first
- Production realism over demos

## License

Apache 2.0 License — use it, extend it, improve it.
