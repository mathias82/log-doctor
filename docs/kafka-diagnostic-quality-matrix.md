# Kafka diagnostic quality matrix

`KafkaOperationalFailureRuleTest` covers every specialized operational Kafka diagnosis with representative log text.

The matrix protects these incident types:

- topic authorization
- consumer-group authorization
- SASL authentication
- producer fencing
- out-of-order producer sequence
- insufficient in-sync replicas
- oversized records
- offset out of range
- rebalance in progress
- unknown topic or partition
- Schema Registry unauthorized responses
- Schema Registry incompatible-schema responses

## Schema Registry context hardening

Schema Registry authorization and compatibility diagnostics require Schema Registry-specific context instead of matching generic HTTP or application prose.

Supported context includes `Schema Registry` / `schema-registry`, Confluent Schema Registry or serializer package names, Schema Registry `/subjects/...` paths, or explicit subject compatibility context. `RestClientException` by itself is intentionally **not** treated as Schema Registry evidence because Spring and other HTTP clients use similarly named exceptions.

The negative matrix includes unrelated Spring `RestClientException` HTTP `401` and `409` responses plus generic schema mismatch text. These must not be classified as Kafka Schema Registry incidents. Positive cases retain realistic Confluent exception formats and subject-path responses.

## Testing intent

The test matrix verifies both positive coverage and representative hard-negative samples. It is intended to catch accidental pattern broadening, missing incident types, cross-system false positives, and drift between real-world Schema Registry error formats and deterministic detection.
