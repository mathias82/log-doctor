package io.github.mathias82.logdoctor.core;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record RemediationMetadata(
        String safety,
        List<String> allowedActions,
        List<String> verificationSteps,
        boolean automaticExecutionAllowed,
        RemediationPlaybook playbook
) {
    public static RemediationMetadata from(Incident incident, Set<FixType> allowedFixes) {
        return build(incident.category(), incident.type(), allowedFixes);
    }

    public static RemediationMetadata from(IncidentCategory category, Set<FixType> allowedFixes) {
        return build(category, null, allowedFixes);
    }

    private static RemediationMetadata build(IncidentCategory category, String incidentType, Set<FixType> allowedFixes) {
        boolean manualOnly = allowedFixes.contains(FixType.NO_AUTOMATIC_FIX);
        List<String> actions = allowedFixes.stream().map(Enum::name).sorted().toList();
        List<String> verification = contextualVerification(incidentType, category);
        RemediationPlaybook playbook = RemediationPlaybook.forIncident(incidentType, category);
        return new RemediationMetadata(
                manualOnly ? "HUMAN_REVIEW_REQUIRED" : "REVIEW_BEFORE_APPLY",
                actions,
                verification,
                false,
                playbook
        );
    }

    private static List<String> contextualVerification(String incidentType, IncidentCategory category) {
        String type = incidentType == null ? "" : incidentType.toUpperCase(Locale.ROOT);

        if (type.equals("JVM OUTOFMEMORYERROR")) {
            return List.of(
                    "Capture heap dump and GC/native-memory evidence before changing limits",
                    "Compare JVM heap settings with container or host memory limits",
                    "Verify memory pressure and allocation behavior under representative load after remediation"
            );
        }
        if (type.equals("HIKARICP CONNECTION TIMEOUT")) {
            return List.of(
                    "Inspect HikariCP active, idle and pending connection metrics during the failure window",
                    "Check for leaked or long-running database connections before increasing pool size",
                    "Verify pool saturation and database latency under representative load after remediation"
            );
        }
        if (type.equals("SPRING_BOOT_STARTUP_FAILURE")) {
            return List.of(
                    "Validate the effective Spring configuration and the reported FailureAnalysis Description/Action",
                    "Reproduce startup with the same profile, environment and external dependencies",
                    "Restart only after reviewing the configuration or dependency change that resolves the startup failure"
            );
        }
        if (type.startsWith("KAFKA_")) {
            return kafkaVerification(type);
        }

        return switch (category) {
            case DESERIALIZATION -> List.of("Re-run the failing payload against the expected schema", "Confirm producer and consumer schema compatibility");
            case CONFIGURATION -> List.of("Validate the effective runtime configuration", "Restart only after reviewing the configuration diff");
            case MEMORY -> List.of("Capture heap/GC evidence before changing limits", "Verify memory pressure after the change under representative load");
            case DATABASE -> List.of("Reproduce against the affected query or transaction", "Verify transaction and persistence behavior after the code change");
            case THREADING -> List.of("Reproduce under concurrent load", "Verify ordering, locking and retry behavior before rollout");
            case INFRASTRUCTURE -> List.of("Confirm dependency health and connectivity", "Review infrastructure telemetry before remediation");
            case BUSINESS -> List.of("Validate the domain invariant with an owner", "Confirm expected state transitions before changing behavior");
            case SECURITY -> List.of("Escalate to a security owner", "Validate authorization/authentication evidence before any change");
            default -> List.of("Reproduce the failure", "Verify the diagnosis with focused tests before rollout");
        };
    }

    private static List<String> kafkaVerification(String type) {
        if (type.contains("AUTHORIZATION") || type.contains("SASL_AUTHENTICATION") || type.contains("SCHEMA_REGISTRY_UNAUTHORIZED")) {
            return List.of(
                    "Confirm the authenticated Kafka or Schema Registry principal and effective credentials",
                    "Verify only the required ACL or authorization scope before retrying",
                    "Confirm authentication and authorization succeed without weakening security controls"
            );
        }
        if (type.contains("NOT_ENOUGH_REPLICAS") || type.contains("UNKNOWN_TOPIC_OR_PARTITION")) {
            return List.of(
                    "Inspect broker/controller health, partition leadership and ISR state",
                    "Confirm topic metadata and replication health before changing durability settings",
                    "Verify producer or consumer recovery after cluster health is restored"
            );
        }
        if (type.contains("OFFSET_OUT_OF_RANGE") || type.contains("REBALANCE_IN_PROGRESS")) {
            return List.of(
                    "Inspect consumer-group membership, committed offsets and recent rebalance activity",
                    "Validate retention, poll cadence and recovery strategy before resetting state",
                    "Verify stable consumption and offset progression after remediation"
            );
        }
        if (type.contains("RECORD_TOO_LARGE")) {
            return List.of(
                    "Measure the serialized record size against producer, topic and broker limits",
                    "Prefer reducing or splitting payloads before increasing message-size limits",
                    "Verify the complete producer-to-broker-to-consumer path with the intended payload size"
            );
        }
        if (type.contains("SCHEMA_INCOMPATIBLE")) {
            return List.of(
                    "Compare the proposed schema with registered subject versions",
                    "Validate the configured compatibility mode and migration plan",
                    "Re-run producer and consumer contract tests before registering the schema"
            );
        }
        if (type.contains("PRODUCER_FENCED") || type.contains("OUT_OF_ORDER_SEQUENCE")) {
            return List.of(
                    "Inspect producer lifecycle, transactional.id uniqueness and idempotence settings",
                    "Confirm broker connectivity and producer restart history around the failure",
                    "Verify transactional or idempotent production under representative retry conditions"
            );
        }
        return List.of(
                "Confirm Kafka broker, topic and client state related to the diagnosed failure",
                "Review relevant producer, consumer and broker telemetry before remediation",
                "Verify recovery with the same client configuration and workload pattern"
        );
    }
}
