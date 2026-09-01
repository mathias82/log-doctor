# Log Doctor 0.3.0

Log Doctor 0.3.0 turns the project from a single-failure explainer into a local-first incident-analysis tool for real JVM log files.

## Highlights

### Multi-incident analysis

Log files are split into failure blocks, diagnosed independently, fingerprinted, and grouped so repeated failures become a single incident with a count instead of repeated noise.

### Timeline and likely incident chains

When comparable timestamps are available, Log Doctor tracks first/last occurrence and observes adjacent incident transitions inside a bounded time window. Root-cause chain candidates receive a heuristic score and LOW / MEDIUM / HIGH confidence label.

These signals are investigative evidence only and do not prove causation.

### Spike detection

Per-minute failure buckets are compared with the observed baseline so abnormal bursts can be surfaced with peak count, peak time and baseline multiplier.

### Incident reports

Batch analysis now generates a Markdown incident report containing the most important grouped incidents, timeline context, root-cause candidates, spikes and investigation order. The Web UI can download this report for use in incidents or postmortems.

### Local-first LLM boundary protection

Sensitive prompt data is deterministically redacted before local Ollama inference. The current best-effort redaction covers common bearer tokens, JWTs, passwords, secrets, API keys, tokens, secret query parameters, email addresses and IPv4 addresses.

If Ollama is unavailable or returns an invalid response, Log Doctor falls back safely rather than treating the failure text as a successful AI diagnosis.

## Correctness improvements

- nested `Caused by:` stack traces stay inside the parent failure block
- timestamped non-failure log entries terminate the previous error block
- offset timestamps are compared using their actual instant
- timestamp-less incidents do not produce invented temporal correlations
- backward transitions are excluded from directional correlations
- volatile numeric, hexadecimal and UUID values are normalized for fingerprint stability
- analysis reports detected failure-block count and whether the 500-block safety cap truncated processing

## Web experience

The embedded dashboard now includes:

- grouped incidents and occurrence counts
- investigation priority
- timeline metadata
- likely correlations
- root-cause candidates
- spike detection
- raw structured batch output
- downloadable Markdown incident report

Uploaded `.log` and `.txt` files are read locally in the browser and sent to the localhost batch endpoint. Raw uploaded files are not persisted by the embedded server.

## API

Single diagnosis remains available at:

```text
POST /api/analyze
```

Batch incident analysis is available at:

```text
POST /api/analyze/batch
```

The API remains localhost-only by default and enforces the configured request and decoded-log limits.

## Build

Requirements:

- Java 21
- Maven 3.9+

```bash
mvn clean verify
java -jar target/log-doctor-0.3.0.jar --web
```

Ollama is optional for deterministic diagnosis and required only for local LLM fallback.

## Upgrade notes

There are no intentional breaking CLI changes from 0.2.0. The main behavioral change is that the Web UI now favors the batch-analysis endpoint and richer incident model.
