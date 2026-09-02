package io.github.mathias82.logdoctor.core;

import java.util.Locale;

/**
 * Stable remediation profile key used to select verification guidance and
 * operational playbooks without spreading incident-type string matching across
 * multiple consumers.
 */
public enum RemediationProfile {
    GENERIC,
    JVM_OUT_OF_MEMORY,
    HIKARI_CONNECTION_TIMEOUT,
    SPRING_BOOT_STARTUP,
    KAFKA_AUTHENTICATION_AUTHORIZATION,
    KAFKA_REPLICATION_METADATA,
    KAFKA_CONSUMER_STATE,
    KAFKA_RECORD_SIZE,
    KAFKA_SCHEMA_COMPATIBILITY,
    KAFKA_PRODUCER_IDEMPOTENCE;

    public static RemediationProfile forIncident(Incident incident) {
        if (incident == null) return GENERIC;
        return forType(incident.type());
    }

    static RemediationProfile forType(String incidentType) {
        String type = incidentType == null ? "" : incidentType.toUpperCase(Locale.ROOT);
        if (type.equals("JVM OUTOFMEMORYERROR")) return JVM_OUT_OF_MEMORY;
        if (type.equals("HIKARICP CONNECTION TIMEOUT")) return HIKARI_CONNECTION_TIMEOUT;
        if (type.equals("SPRING_BOOT_STARTUP_FAILURE")) return SPRING_BOOT_STARTUP;
        if (!type.startsWith("KAFKA_")) return GENERIC;

        if (type.contains("AUTHORIZATION") || type.contains("SASL_AUTHENTICATION") || type.contains("SCHEMA_REGISTRY_UNAUTHORIZED")) {
            return KAFKA_AUTHENTICATION_AUTHORIZATION;
        }
        if (type.contains("NOT_ENOUGH_REPLICAS") || type.contains("UNKNOWN_TOPIC_OR_PARTITION")) {
            return KAFKA_REPLICATION_METADATA;
        }
        if (type.contains("OFFSET_OUT_OF_RANGE") || type.contains("REBALANCE_IN_PROGRESS")) {
            return KAFKA_CONSUMER_STATE;
        }
        if (type.contains("RECORD_TOO_LARGE")) return KAFKA_RECORD_SIZE;
        if (type.contains("SCHEMA_INCOMPATIBLE")) return KAFKA_SCHEMA_COMPATIBILITY;
        if (type.contains("PRODUCER_FENCED") || type.contains("OUT_OF_ORDER_SEQUENCE")) {
            return KAFKA_PRODUCER_IDEMPOTENCE;
        }
        return GENERIC;
    }
}
