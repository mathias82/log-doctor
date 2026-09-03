package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * High-fidelity Kafka operational diagnoses that add context beyond the broad
 * exception catalog. These cases are deliberately handled before the catalog so
 * operators get Kafka-specific root-cause guidance and remediation.
 */
public final class KafkaOperationalFailureRule implements IncidentRule {

    private static final List<Spec> SPECS = List.of(
            new Spec("KAFKA_TOPIC_AUTHORIZATION_FAILED", "topicauthorizationexception", IncidentCategory.SECURITY,
                    Severity.HIGH, "Kafka ACL", "Kafka topic authorization failed",
                    "The client principal is not authorized for one or more Kafka topics.",
                    "Verify the authenticated principal, topic ACLs and the exact topic name. Do not bypass authorization checks."),
            new Spec("KAFKA_GROUP_AUTHORIZATION_FAILED", "groupauthorizationexception", IncidentCategory.SECURITY,
                    Severity.HIGH, "Kafka consumer group ACL", "Kafka consumer-group authorization failed",
                    "The consumer principal is not authorized for the configured group.id.",
                    "Verify group.id and grant only the required consumer-group ACL to the authenticated principal."),
            new Spec("KAFKA_SASL_AUTHENTICATION_FAILED", "saslauthenticationexception", IncidentCategory.SECURITY,
                    Severity.HIGH, "Kafka SASL authentication", "Kafka SASL authentication failed",
                    "The broker rejected the client's SASL credentials or mechanism.",
                    "Check security.protocol, sasl.mechanism, JAAS/credential configuration and secret freshness."),
            new Spec("KAFKA_PRODUCER_FENCED", "producerfencedexception", IncidentCategory.THREADING,
                    Severity.HIGH, "Kafka transactional producer", "Kafka transactional producer was fenced",
                    "Another producer instance is using the same transactional.id or the current producer epoch is no longer valid.",
                    "Ensure transactional.id is unique per active producer instance and review producer lifecycle before retrying transactions."),
            new Spec("KAFKA_OUT_OF_ORDER_SEQUENCE", "outofordersequenceexception", IncidentCategory.INFRASTRUCTURE,
                    Severity.HIGH, "Kafka idempotent producer", "Kafka producer sequence is out of order",
                    "The broker observed a producer sequence that is inconsistent with the expected idempotent-producer state.",
                    "Review producer restarts, broker connectivity and idempotence/transaction settings; recreate the producer when required."),
            new Spec("KAFKA_NOT_ENOUGH_REPLICAS", "notenoughreplicasexception", IncidentCategory.INFRASTRUCTURE,
                    Severity.HIGH, "Kafka replication", "Kafka cannot satisfy the required replica acknowledgements",
                    "The partition does not currently have enough in-sync replicas for the configured durability requirements.",
                    "Inspect ISR shrinkage, broker health and min.insync.replicas. Restore replica health instead of weakening durability settings blindly."),
            new Spec("KAFKA_RECORD_TOO_LARGE", "recordtoolargeexception", IncidentCategory.CONFIGURATION,
                    Severity.MEDIUM, "Kafka producer/broker limits", "Kafka record exceeds the configured size limit",
                    "The serialized record is larger than a producer, broker or topic request/message limit.",
                    "Measure the serialized payload and align producer max.request.size with broker/topic message limits, or reduce/split the payload."),
            new Spec("KAFKA_OFFSET_OUT_OF_RANGE", "offsetoutofrangeexception", IncidentCategory.INFRASTRUCTURE,
                    Severity.HIGH, "Kafka consumer offsets", "Kafka consumer requested an unavailable offset",
                    "The requested offset is outside the partition's retained log range, commonly after retention or offset-state drift.",
                    "Inspect committed offsets and retention. Choose an explicit recovery/reset strategy before resuming consumption."),
            new Spec("KAFKA_REBALANCE_IN_PROGRESS", "rebalanceinprogressexception", IncidentCategory.INFRASTRUCTURE,
                    Severity.MEDIUM, "Kafka consumer group", "Kafka consumer operation collided with an active rebalance",
                    "The consumer group is rebalancing while the client attempted a group-dependent operation.",
                    "Review poll cadence, processing duration, session/max.poll settings and membership churn; retry only through the consumer's normal rebalance flow."),
            new Spec("KAFKA_UNKNOWN_TOPIC_OR_PARTITION", "unknowntopicorpartitionexception", IncidentCategory.INFRASTRUCTURE,
                    Severity.HIGH, "Kafka metadata", "Kafka topic or partition is unavailable in metadata",
                    "The broker cannot currently resolve the requested topic or partition.",
                    "Verify topic existence/name, metadata propagation, broker/controller health and client bootstrap connectivity."),
            new Spec("KAFKA_SCHEMA_REGISTRY_UNAUTHORIZED", "schema-registry-unauthorized", IncidentCategory.SECURITY,
                    Severity.HIGH, "Schema Registry authentication", "Schema Registry rejected the client as unauthorized",
                    "The Schema Registry request was rejected because authentication credentials are missing or invalid.",
                    "Verify Schema Registry URL and authentication credentials. Rotate or repair credentials rather than disabling authentication."),
            new Spec("KAFKA_SCHEMA_INCOMPATIBLE", "schema-registry-incompatible", IncidentCategory.DESERIALIZATION,
                    Severity.HIGH, "Schema Registry compatibility", "Schema Registry rejected an incompatible schema",
                    "The proposed schema violates the subject's configured compatibility policy.",
                    "Compare the new schema with registered versions and make a compatible evolution; do not weaken compatibility without an explicit migration plan.")
    );

    @Override
    public Optional<Incident> match(RuleContext context) {
        String text = context.contextText() == null ? "" : context.contextText();
        String lower = text.toLowerCase(Locale.ROOT);
        for (Spec spec : SPECS) {
            if (!matches(spec, lower)) continue;
            CatalogIncident incident = new CatalogIncident(spec.type(), spec.category(), spec.severity(), Confidence.HIGH,
                    spec.component(), spec.summary(), spec.rootCause(), spec.recommendation());
            incident.setEvidence(firstMatchingLine(text, evidenceMarker(spec, lower)));
            return Optional.of(incident);
        }
        return Optional.empty();
    }

    private static boolean matches(Spec spec, String lower) {
        return switch (spec.type()) {
            case "KAFKA_SCHEMA_REGISTRY_UNAUTHORIZED" -> hasSchemaRegistryContext(lower)
                    && (lower.contains("unauthorized") || lower.contains("status 401") || lower.contains("status: 401") || lower.contains("error code: 401"));
            case "KAFKA_SCHEMA_INCOMPATIBLE" -> hasSchemaRegistryContext(lower)
                    && (lower.contains("incompatible schema") || lower.contains("is incompatible with") || lower.contains("error code: 409") || lower.contains("status 409"));
            default -> lower.contains(spec.marker());
        };
    }

    private static boolean hasSchemaRegistryContext(String lower) {
        return lower.contains("schema registry")
                || lower.contains("schema-registry")
                || lower.contains("schemaregistry")
                || lower.contains("io.confluent.kafka.schemaregistry")
                || lower.contains("io.confluent.kafka.serializers")
                || lower.contains("/subjects/")
                || lower.contains("subject ") && lower.contains("compatibility");
    }

    private static String evidenceMarker(Spec spec, String lower) {
        if (spec.type().equals("KAFKA_SCHEMA_REGISTRY_UNAUTHORIZED")) {
            if (lower.contains("unauthorized")) return "unauthorized";
            return "401";
        }
        if (spec.type().equals("KAFKA_SCHEMA_INCOMPATIBLE")) {
            if (lower.contains("incompatible schema")) return "incompatible schema";
            if (lower.contains("is incompatible with")) return "is incompatible with";
            return "409";
        }
        return spec.marker();
    }

    private static String firstMatchingLine(String text, String marker) {
        return text.lines()
                .filter(line -> line.toLowerCase(Locale.ROOT).contains(marker))
                .findFirst()
                .orElse(text.lines().findFirst().orElse(text));
    }

    private record Spec(String type, String marker, IncidentCategory category, Severity severity,
                        String component, String summary, String rootCause, String recommendation) {}
}
