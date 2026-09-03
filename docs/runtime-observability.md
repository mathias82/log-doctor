# Runtime observability

Log Doctor exposes privacy-safe in-process operational metrics from the embedded web server in two formats:

```text
GET /api/metrics   # JSON
GET /metrics       # Prometheus text exposition
```

The endpoints are intended for local operations, smoke checks, dashboards and metrics pipelines. They do not expose raw logs, evidence, prompts, exception messages, tenant identifiers or LLM responses.

## Metrics

- completed analyses
- deterministic diagnoses
- unknown diagnoses
- no-failure results
- local LLM usage
- unexpected analysis errors
- average completed analysis latency
- maximum completed analysis latency

Prometheus names use the `log_doctor_` prefix, for example `log_doctor_analyses_total` and `log_doctor_analysis_latency_average_ms`.

Counters are process-local and reset when the Log Doctor process restarts. They are operational telemetry, not durable audit records.

## Prometheus

Point a Prometheus scrape job at `http://<log-doctor-host>:8080/metrics`. The endpoint uses the Prometheus text exposition format and keeps the existing Log Doctor API-version response header.

## OpenTelemetry Collector

`observability/otel-collector-config.yaml` provides a collector bridge that uses the OpenTelemetry Collector Prometheus receiver to scrape Log Doctor's `/metrics` endpoint. The included configuration exports to the collector `debug` exporter so it is safe to run as a local verification pipeline.

For production, replace or extend the exporter with the OTLP-compatible backend used by your platform. This keeps Log Doctor independent from a specific metrics vendor while still fitting an OpenTelemetry pipeline.

When the collector runs in Docker while Log Doctor runs on the host, the sample target is `host.docker.internal:8080`. Change that target to the Log Doctor service DNS name when both run on the same container network.

## Privacy boundary

Metrics deliberately contain only aggregate counters and timings. Raw log content and diagnostic evidence stay outside the metrics surface. This keeps observability aligned with Log Doctor's local-first design.

## Scope

The current implementation is intentionally lightweight: Log Doctor owns the operational metric contract and Prometheus scrape surface, while OpenTelemetry Collector handles protocol/backend adaptation. Diagnosis payloads and remediation safety policy are unchanged.
