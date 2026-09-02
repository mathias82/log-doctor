package io.github.mathias82.logdoctor.core;

import java.util.List;

/**
 * Investigation-first operational playbook. Steps are guidance only and are
 * never executable instructions; RemediationMetadata remains the safety gate.
 */
public record RemediationPlaybook(
        List<String> inspect,
        List<String> changeCandidates,
        List<String> validate,
        List<String> escalationSignals
) {
    static RemediationPlaybook forProfile(RemediationProfile profile, IncidentCategory category) {
        return switch (profile) {
            case JVM_OUT_OF_MEMORY -> new RemediationPlaybook(
                    List.of("Heap dump and GC logs", "Container/host memory limits and JVM memory flags"),
                    List.of("Remove confirmed retention/leak source", "Adjust JVM/container memory only after evidence review"),
                    List.of("Repeat representative load and compare heap/GC pressure", "Confirm no container OOM kill or allocation failure"),
                    List.of("Repeated OOM after rollback or memory adjustment", "Native-memory growth without a heap explanation")
            );
            case HIKARI_CONNECTION_TIMEOUT -> new RemediationPlaybook(
                    List.of("Hikari active/idle/pending metrics", "Slow queries, transaction duration and connection leak evidence"),
                    List.of("Fix confirmed connection leaks or long transactions", "Tune pool size/timeouts only against database capacity"),
                    List.of("Load-test pool saturation and database latency", "Confirm pending connections return to normal"),
                    List.of("Database saturation or failover symptoms", "Pool exhaustion persists with no application-side leak")
            );
            case SPRING_BOOT_STARTUP -> new RemediationPlaybook(
                    List.of("FailureAnalysis Description and Action", "Effective profiles, configuration sources and dependency availability"),
                    List.of("Correct the identified configuration/dependency mismatch", "Revert the startup-affecting change when evidence points to a regression"),
                    List.of("Start with the same profile and environment", "Run focused configuration/startup tests before rollout"),
                    List.of("Failure depends on an unavailable external service", "Configuration source or secret ownership is unclear")
            );
            case KAFKA_AUTHENTICATION_AUTHORIZATION -> new RemediationPlaybook(
                    List.of("Authenticated principal and credential source", "Effective Kafka/Schema Registry ACLs and resource names"),
                    List.of("Repair or rotate invalid credentials", "Grant only the minimum missing authorization"),
                    List.of("Retry authentication/authorization with the same principal", "Confirm access works without broader privileges"),
                    List.of("Credential compromise is suspected", "Required permission crosses an ownership/security boundary")
            );
            case KAFKA_REPLICATION_METADATA -> new RemediationPlaybook(
                    List.of("Broker/controller health", "Partition leadership, ISR and topic metadata"),
                    List.of("Restore broker/replica health", "Correct a confirmed topic or metadata configuration issue"),
                    List.of("Confirm ISR/leadership recovery", "Retry the same producer/consumer operation without weakening durability"),
                    List.of("ISR continues shrinking", "Controller instability or repeated broker loss")
            );
            case KAFKA_CONSUMER_STATE -> new RemediationPlaybook(
                    List.of("Consumer-group membership and rebalance history", "Committed offsets, retention and poll timing"),
                    List.of("Correct poll/processing settings causing churn", "Reset offsets only with an explicit data-recovery decision"),
                    List.of("Confirm stable group membership", "Verify monotonic offset progress and expected replay/loss semantics"),
                    List.of("Offset reset could lose business data", "Repeated rebalances continue after client tuning")
            );
            case KAFKA_RECORD_SIZE -> new RemediationPlaybook(
                    List.of("Serialized payload size", "Producer, topic and broker message-size limits"),
                    List.of("Reduce or split the payload", "Align limits only when the larger payload is explicitly supported"),
                    List.of("Exercise producer-to-consumer flow with boundary-size payloads", "Confirm broker and consumer limits remain aligned"),
                    List.of("Payload growth is unbounded", "Changing broker-wide limits would affect unrelated workloads")
            );
            case KAFKA_SCHEMA_COMPATIBILITY -> new RemediationPlaybook(
                    List.of("Current subject compatibility mode", "Proposed schema versus registered versions"),
                    List.of("Make the schema evolution compatible", "Use a migration plan instead of silently weakening compatibility"),
                    List.of("Run producer/consumer contract tests", "Register and consume the schema in a non-production environment first"),
                    List.of("Compatibility change affects multiple teams", "Migration requires coordinated producer/consumer rollout")
            );
            case KAFKA_PRODUCER_IDEMPOTENCE -> new RemediationPlaybook(
                    List.of("transactional.id ownership and producer lifecycle", "Idempotence settings, restarts and broker connectivity"),
                    List.of("Ensure transactional.id uniqueness per active producer", "Recreate producer state only through the supported client lifecycle"),
                    List.of("Run transactional/idempotent production with retries", "Confirm no duplicate active producer identity"),
                    List.of("Multiple deployments share transactional identity", "Ordering failures persist after clean producer recreation")
            );
            case GENERIC -> categoryFallback(category);
        };
    }

    private static RemediationPlaybook categoryFallback(IncidentCategory category) {
        return switch (category) {
            case SECURITY -> new RemediationPlaybook(
                    List.of("Authentication/authorization evidence and audit context"),
                    List.of("Security-owner-approved credential or authorization correction"),
                    List.of("Verify least-privilege access after the change"),
                    List.of("Suspected compromise or unclear authorization ownership")
            );
            case MEMORY -> new RemediationPlaybook(
                    List.of("Heap, GC and runtime memory telemetry"),
                    List.of("Change memory behavior or limits only after identifying pressure source"),
                    List.of("Repeat representative load and compare memory pressure"),
                    List.of("Memory growth remains unexplained")
            );
            default -> new RemediationPlaybook(
                    List.of("Failure evidence, cause chain and relevant runtime telemetry"),
                    List.of("Apply only a change directly supported by the diagnosis"),
                    List.of("Reproduce the original scenario and run focused regression tests"),
                    List.of("Evidence is conflicting, incomplete or crosses an ownership boundary")
            );
        };
    }
}
