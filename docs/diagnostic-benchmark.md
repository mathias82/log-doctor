# Diagnostic benchmark

Log Doctor includes a curated deterministic diagnostic corpus at `src/test/resources/diagnostic-benchmark/corpus.json`.

The benchmark is executed by `DiagnosticBenchmarkTest` during normal Maven verification. The corpus now contains **120 labelled cases** split evenly across four operational domains: **JVM, Spring, Kafka and DB**. Each domain contains positive failures plus realistic and hard-negative non-failures.

## Metrics

The benchmark records both aggregate and per-category metrics:

- precision = true positives / all positive predictions
- recall = true positives / all known positive cases
- false-positive rate = false positives / all negative cases
- exact rule accuracy = positive cases routed to the expected deterministic rule

Aggregate quality gates remain:

- precision >= 0.95
- recall >= 0.90
- false-positive rate <= 0.05
- exact rule accuracy >= 0.90

Category-level gates prevent a strong domain from hiding regressions in another domain:

- precision >= 0.90 for each category
- recall >= 0.85 for each category
- false-positive rate <= 0.10 for each category
- exact rule accuracy >= 0.85 for each category
- at least 25 labelled cases per category
- at least 100 labelled cases overall

CI writes the machine-readable result to `target/diagnostic-benchmark.json`, includes it in the GitHub Actions job summary and uploads it as the `diagnostic-benchmark` artifact. The report contains a `categories` object with independent JVM, Spring, Kafka and DB confusion counts and quality metrics.

## Corpus policy

Positive cases cover JVM runtime failures, Spring/Spring Boot startup and web failures, Kafka/Schema Registry operational failures, and Hibernate/JPA/JDBC/Hikari database failures. Negative cases contain operationally plausible but non-failing messages that should not be classified as incidents.

The corpus also contains **hard negatives**: text that looks similar to a supported failure but belongs to a different system or context. Examples include ordinary HTTP `401` / `409` responses from Spring REST clients and generic schema-mismatch messages that must not be promoted to Kafka Schema Registry incidents.

New deterministic rules should add positive examples and confusing negative examples to the relevant category. A rule should not improve recall by silently increasing false positives. When a false-positive regression is fixed, the triggering log should remain in the corpus so the bug cannot reappear unnoticed.

## Context-sensitive rules

Rules that depend on a shared exception name should require subsystem evidence rather than treating the exception name alone as sufficient context. For example, Schema Registry matching requires Confluent/Schema Registry identifiers, a Schema Registry subject path, or explicit compatibility context. A generic `RestClientException` is not enough.

## Interpretation

These metrics are regression gates for the checked-in curated corpus. They are not a claim of production-wide statistical accuracy. The corpus is intentionally synthetic/curated and reproducible. Production precision requires a larger independently labelled dataset from real workloads and should be tracked separately from this deterministic regression benchmark.
