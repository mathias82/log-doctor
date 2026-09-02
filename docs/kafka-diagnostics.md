# Kafka deep diagnostics

Log Doctor applies specialized deterministic Kafka operational diagnostics before the broad exception catalog. The goal is to return Kafka-specific root-cause and remediation guidance without sending well-known operational failures to Ollama.

Covered high-fidelity scenarios include:

- topic and consumer-group authorization failures
- SASL authentication failures
- transactional producer fencing
- idempotent producer sequence failures
- insufficient in-sync replicas
- oversized records
- offsets outside the retained log range
- consumer-group rebalance-in-progress failures
- unavailable topic/partition metadata
- Schema Registry authentication failures
- incompatible schema registration

## Safety

The diagnostics preserve the existing `FixPolicy`. Infrastructure, security, concurrency and transactional failures are diagnosis targets, not permission to mutate production configuration automatically.

Recommendations intentionally avoid unsafe shortcuts such as disabling authentication, weakening durability settings, blindly resetting offsets or weakening Schema Registry compatibility without an explicit migration plan.

## Deterministic precedence

Existing highly specific Kafka rules still run first. `KafkaOperationalFailureRule` then provides deeper operational guidance for common Kafka failures before `CommonFailureCatalogRule` is considered. Unknown or application-specific failures can still reach optional local Ollama analysis.
