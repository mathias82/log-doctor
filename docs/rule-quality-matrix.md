# Deterministic Rule Quality Matrix

Log Doctor relies on deterministic diagnosis before optional Ollama enrichment. This document defines the baseline regression matrix that protects that contract as the rule catalog grows.

## What the matrix validates

The matrix exercises representative failures across JVM, Spring, Hibernate/JPA and Kafka and asserts that the expected specialized rule wins. It also includes representative non-failure log lines that must remain unmatched.

Current coverage includes:

- JVM heap exhaustion and stack overflow
- NullPointerException
- Hibernate LazyInitializationException
- Hikari connection timeout
- Spring configuration binding
- missing Spring beans
- Spring Boot startup failure analysis
- optimistic locking
- Kafka authorization
- Kafka rebalance / commit failure
- Kafka unknown topic or partition
- benign INFO, DEBUG and WARN examples that should not trigger deterministic incidents

## Why this matters

The broad catalog intentionally sits behind specialized rules. As new signatures are added, an overly broad pattern can silently steal traffic from a richer rule or begin matching normal operational messages. The quality matrix catches both precedence regressions and obvious false positives in CI.

The matrix is not intended to prove that every production log variant is covered. It is a stable, high-signal regression baseline. New deterministic rules should add at least one positive case and, when the matcher is broad, one negative case.

## Rule contribution checklist

For every new or materially changed deterministic rule:

1. Add a representative production-style positive sample.
2. Assert the expected rule class, not only that some incident was detected.
3. Add a negative sample when the matcher uses generic words, HTTP codes or framework-level exception names.
4. Verify specialized-rule precedence when a broad fallback can also match.
5. Keep `NO_AUTOMATIC_FIX` and human-review semantics independent from detection confidence.

This matrix complements the dedicated per-rule unit tests; it does not replace them.
