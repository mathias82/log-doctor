# Diagnostic benchmark

Log Doctor includes a curated deterministic diagnostic corpus at `src/test/resources/diagnostic-benchmark/corpus.json`.

The benchmark is executed by `DiagnosticBenchmarkTest` during normal Maven verification and measures aggregate detector quality rather than only individual rule examples.

## Metrics

The benchmark records:

- precision = true positives / all positive predictions
- recall = true positives / all known positive cases
- false-positive rate = false positives / all negative cases
- exact rule accuracy = positive cases routed to the expected deterministic rule

The current quality gates are:

- precision >= 0.95
- recall >= 0.90
- false-positive rate <= 0.05
- exact rule accuracy >= 0.90

CI writes the machine-readable result to `target/diagnostic-benchmark.json`, includes it in the GitHub Actions job summary and uploads it as the `diagnostic-benchmark` artifact.

## Corpus policy

Positive cases cover representative JVM, Spring, Hibernate/JPA, JDBC/Hikari and Kafka failures. Negative cases contain operationally plausible but non-failing messages that should not be classified as incidents.

New deterministic rules should add both positive examples and confusing negative examples. A rule should not improve recall by silently increasing false positives.

## Interpretation

These metrics are regression gates for the checked-in curated corpus. They are not a claim of production-wide statistical accuracy. Production precision requires a larger independently labelled dataset from real workloads and should be tracked separately from this deterministic regression benchmark.
