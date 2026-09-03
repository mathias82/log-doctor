# Performance benchmark

Log Doctor includes a synthetic deterministic performance benchmark for batch analysis.

The benchmark is intentionally designed as a reproducible regression signal rather than a production SLA. It runs with a no-op LLM client so measurements reflect deterministic parsing, diagnosis, grouping, correlation, reporting and batch-safety behavior without network/model variance.

## Scenarios

`PerformanceBenchmarkTest` currently measures:

- 50 incident blocks with short stack traces
- 200 incident blocks with medium stack traces
- 500 incident blocks at the supported processing cap
- 750 incident blocks to verify truncation at the 500-block safety cap
- a synthetic log of approximately 2 MiB containing 500 failure blocks plus non-failure traffic

Each scenario uses warmup iterations before measured iterations.

## Metrics

The generated `target/performance-benchmark.json` contains, per scenario:

- input bytes and line count
- detected failure blocks
- unique incidents
- whether the batch was truncated
- average latency
- p50 latency
- p95 latency
- p99 latency
- approximate throughput in MiB/s at p50 latency
- maximum observed heap-use delta during a measured iteration

The report also records Java runtime metadata such as Java version, VM name, processor count and configured maximum heap.

## CI

`.github/workflows/performance-benchmark.yml` runs the benchmark on relevant pull requests and through `workflow_dispatch`.

The workflow publishes `target/performance-benchmark.json` to the GitHub Actions job summary and uploads it as the `performance-benchmark` artifact.

Absolute latency and heap values are not hard quality gates because hosted CI machines vary. Correctness properties such as incident detection and the 500-block truncation contract remain asserted. Performance regressions should be evaluated by comparing runs under similar runner conditions.

## Interpretation

This benchmark answers questions such as:

- how batch latency changes as incident volume grows
- how close-to-limit and over-limit inputs behave
- whether p95/p99 latency shifts substantially between revisions
- whether throughput or approximate heap behavior regresses unexpectedly

It does not claim production capacity, maximum supported throughput, memory-leak freedom or end-to-end LLM performance.
