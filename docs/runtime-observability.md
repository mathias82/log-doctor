# Runtime observability

Log Doctor exposes privacy-safe in-process operational metrics from the embedded web server at:

```text
GET /api/metrics
```

The endpoint is intended for local operations, smoke checks, dashboards, and future Prometheus/OpenTelemetry adapters. It does not expose raw logs, evidence, prompts, exception messages, tenant identifiers, or LLM responses.

## Metrics

- `analyses` — completed analysis requests observed by the web server
- `deterministicDiagnoses` — requests classified as deterministic diagnoses
- `unknownDiagnoses` — requests with no deterministic match
- `noFailure` — requests where no failure was detected
- `llmUsed` — analysis requests whose result reports local LLM usage
- `analysisErrors` — unexpected analysis failures returned as HTTP 500
- `averageLatencyMs` — average completed analysis latency
- `maxLatencyMs` — maximum completed analysis latency since server start

Counters are process-local and reset when the Log Doctor process restarts. They are operational telemetry, not durable audit records.

## Privacy boundary

Metrics deliberately contain only aggregate counters and timings. Raw log content and diagnostic evidence stay outside the metrics surface. This keeps observability aligned with Log Doctor's local-first design.

## Scope

This is a lightweight baseline rather than a full metrics backend. A future adapter can translate the same operational signals into Prometheus or OpenTelemetry without changing diagnosis payloads or the remediation safety model.
