# Runtime observability

Log Doctor exposes privacy-safe in-process operational metrics from the embedded web server:

```text
GET /api/metrics   # JSON
GET /metrics       # Prometheus text exposition
```

The endpoints do not expose raw logs, evidence, prompts, exception messages, tenant identifiers or LLM responses.

## Metrics

The runtime surface tracks request-level and incident-level signals separately:

- completed analysis requests
- incidents returned across requests
- deterministic diagnosis outcomes
- unknown diagnosis outcomes
- no-failure results
- local LLM usage
- unexpected analysis errors
- fail-soft custom rule/provider failures
- average and maximum completed-analysis latency
- Prometheus latency histogram buckets, sum and count

Prometheus names use the `log_doctor_` prefix. Important series include `log_doctor_analyses_total`, `log_doctor_incidents_total`, `log_doctor_rule_provider_failures_total`, and the `log_doctor_analysis_latency_milliseconds` histogram family.

The histogram uses fixed millisecond buckets at 10, 25, 50, 100, 250, 500, 1000, 2500, 5000 and 10000 ms plus `+Inf`. This allows Prometheus-side percentile estimation without turning shared-runner or local observations into application SLAs.

Batch requests are classified as `UNKNOWN` only when their returned incidents are unknown-only. A batch containing at least one known diagnosis is classified as diagnosed. Incident count remains separate from request count so a large grouped batch does not look like a single diagnostic event.

Counters are process-local and reset when the process restarts. They are operational telemetry, not durable audit records.

## Prometheus and OpenTelemetry

Point Prometheus at `http://<log-doctor-host>:8080/metrics`. `observability/otel-collector-config.yaml` provides a vendor-neutral OpenTelemetry Collector bridge using the Prometheus receiver and a safe local debug exporter. Replace or extend the exporter with the OTLP-compatible backend used by your platform.

## Privacy boundary

Metrics contain aggregate counters and timings only. Raw log content and diagnostic evidence stay outside the metrics surface. Diagnosis payloads, `NO_AUTOMATIC_FIX`, and remediation execution policy are unchanged.
