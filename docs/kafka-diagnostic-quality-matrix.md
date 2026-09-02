# Kafka diagnostic quality matrix

`KafkaOperationalFailureRuleTest` now covers every specialized operational Kafka diagnosis with representative log text.

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

Supported context includes phrases such as `Schema Registry`, `schema-registry`, Confluent Schema Registry package names, or `RestClientException` together with the relevant authorization/compatibility signal.

This prevents examples such as a generic `401 Unauthorized` response from another REST API or an application message containing `incompatible schema` from being classified as Kafka Schema Registry incidents.

## Testing intent

The test matrix verifies both positive coverage and representative negative samples. It is intended to catch accidental pattern broadening, missing incident types, and drift between real-world Schema Registry error formats and deterministic detection.
