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

The top-level `correctnessAssertions` field is `PASSED` only after the benchmark's deterministic correctness assertions complete. It remains separate from trend warnings.

## CI

`.github/workflows/performance-benchmark.yml` runs the benchmark on relevant pull requests, relevant pushes to `main`, and through `workflow_dispatch`.

The workflow publishes `target/performance-benchmark.json` to the GitHub Actions job summary and uploads it as the `performance-benchmark` artifact. A successful `main` run becomes the candidate baseline for later runs.

Absolute latency and heap values are not hard quality gates because hosted CI machines vary. Correctness properties such as incident detection and the 500-block truncation contract remain asserted. Performance regressions should be evaluated by comparing runs under similar runner conditions.

## Trend comparison

After producing the current raw report, CI locates the latest successful `main` run of the same workflow and downloads its `performance-benchmark` artifact. If no baseline exists yet, the current raw report is still published and the summary explains that comparison was skipped.

`PerformanceBenchmarkComparisonTest` compares matching scenarios and writes:

- `target/performance-comparison.json` for downstream tooling;
- `target/performance-comparison.md` for the GitHub Actions job summary.

The comparison reports relative changes for p50, p95 and p99 latency, throughput at p50, and maximum observed heap delta. New scenarios are reported as additive instead of forcing snapshot churn; scenarios present only in the baseline are also listed.

Warnings use conservative percentage thresholds rather than absolute timings. Workflow defaults are:

- latency regression greater than 25%;
- throughput regression greater than 20%;
- observed heap-delta regression greater than 50%.

The values are configurable through the workflow environment and the corresponding Maven properties:

- `performance.warnLatencyPercent`;
- `performance.warnThroughputPercent`;
- `performance.warnHeapPercent`;
- `performance.runtimeHeapTolerancePercent`.

Warnings are non-blocking. Existing correctness assertions still fail the benchmark test normally, including the 500-block processing cap. A warning therefore cannot hide or replace a correctness failure.

## Runtime compatibility

Comparisons are only evaluated when material runtime metadata is compatible. The gate checks Java feature version, VM name, operating system, architecture, active processor count and configured maximum heap. Patch-level Java version differences are reported but are not material when the Java feature version is unchanged.

The workflow fixes the benchmark JVM to two active processors and a 1 GiB maximum heap to reduce runner-to-runner configuration drift. If material metadata differs, the report uses `INCOMPATIBLE_ENVIRONMENT`, lists the differences and does not calculate performance warnings. This avoids presenting hardware/runtime changes as code regressions.

## Baseline selection and refresh

The default baseline is the artifact from the most recent successful `push` run on the repository's default branch. A new baseline is refreshed naturally after an intentional change is reviewed, merged to `main`, and the performance workflow succeeds.

For a local or explicitly selected comparison, first generate the current report and then point the comparison test at any preserved baseline report:

```bash
mvn -Dtest=PerformanceBenchmarkTest test
mvn -Dtest=PerformanceBenchmarkComparisonTest \
  -Dperformance.current=target/performance-benchmark.json \
  -Dperformance.baseline=/path/to/performance-benchmark.json \
  test
```

Only select a baseline produced by a materially compatible runtime. Preserve the raw baseline and current JSON alongside the comparison when investigating a warning.

## Interpretation

This benchmark answers questions such as:

- how batch latency changes as incident volume grows
- how close-to-limit and over-limit inputs behave
- whether p95/p99 latency shifts substantially between revisions
- whether throughput or approximate heap behavior regresses unexpectedly

It does not claim production capacity, maximum supported throughput, memory-leak freedom or end-to-end LLM performance.
