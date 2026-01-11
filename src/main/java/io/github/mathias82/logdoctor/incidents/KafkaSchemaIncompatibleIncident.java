package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class KafkaSchemaIncompatibleIncident extends Incident {

    @Override
    public String type() {
        return "Kafka Schema Registry Incompatibility";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.CONFIGURATION;
    }

    @Override
    public Severity severity() {
        return Severity.CRITICAL;
    }

    @Override
    public Confidence confidence() {
        return Confidence.HIGH;
    }

    @Override
    public String summary() {
        return "An Avro schema change violated compatibility rules.";
    }

    @Override
    public String rootCause() {
        return "Schema evolution was not backward-compatible.";
    }

    @Override
    public String recommendation() {
        return "Fix schema evolution and validate compatibility before deployment.";
    }
}
