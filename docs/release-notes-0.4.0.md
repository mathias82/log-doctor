# Log Doctor 0.4.0

Log Doctor 0.4.0 turns the local diagnostic engine into a reproducible containerized stack and strengthens the boundary between deterministic analysis and optional local AI.

## Highlights

### One-command full stack

Run Log Doctor, Ollama and model bootstrap together:

```bash
docker compose up -d --build
```

The application is then available at `http://localhost:8080`.

### Real Ollama end-to-end validation

The repository now validates real local-model inference in CI instead of relying only on mocked LLM clients. The Docker-backed E2E paths submit an unknown JVM failure and verify that the response reports `llmUsed=true`.

### Deduplicated model usage

Batch analysis performs its deterministic pass and incident fingerprinting before optional LLM enrichment. Repeated unknown failures therefore use at most one model request per unique unknown fingerprint instead of one request per occurrence.

### Full-stack Docker E2E

CI builds the Log Doctor image, starts the complete Compose stack, waits for application health, submits an unknown failure through `/api/analyze/batch`, verifies real Ollama-backed enrichment, and tears the stack down.

## Reliability and correctness

0.4.0 also hardens several edge cases:

- clean logs produce zero detected failure blocks
- structured log-level parsing avoids false positives from words such as `INFO` inside message text
- `WARNING` is normalized to `WARN`
- the 500-block safety cap and truncation metadata have regression coverage
- `uniqueIncidents` is explicitly serialized by the batch API
- valid 5 MB decoded logs remain accepted when JSON escaping expands the HTTP request envelope
- first/last occurrence ordering handles out-of-order records safely
- incompatible timestamp bases do not create unsafe timeline comparisons or correlations
- browser-side utilities have automated tests for escaping, upload validation and privacy-safe bounded history

## Configuration

Direct Java execution keeps safe local defaults:

```text
bind address: 127.0.0.1
Ollama URL:    http://localhost:11434
Ollama model:  qwen2.5:3b
```

Container deployments can configure:

```text
LOG_DOCTOR_BIND_ADDRESS
LOG_DOCTOR_OLLAMA_URL
LOG_DOCTOR_OLLAMA_MODEL
LOG_DOCTOR_PORT
```

The full Compose stack configures the application to reach Ollama over the internal Docker network.

## Upgrade

Build the executable JAR:

```bash
mvn clean verify
java -jar target/log-doctor-0.4.0.jar --web
```

Or use Docker only:

```bash
docker compose up -d --build
```

## Release safety

Deterministic diagnosis remains authoritative. Unknown incidents fall back safely when Ollama is unavailable, sensitive prompt data is redacted before the LLM boundary, and direct host execution remains localhost-only unless a different bind address is explicitly requested.
