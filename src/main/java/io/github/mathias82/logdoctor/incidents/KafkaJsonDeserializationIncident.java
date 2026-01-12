package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class KafkaJsonDeserializationIncident extends Incident {

    @Override
    public String type() {
        return "Kafka JSON Deserialization Failure";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.DESERIALIZATION;
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public Confidence confidence() {
        return Confidence.HIGH;
    }

    @Override
    public String summary() {
        return "Kafka consumer failed to deserialize JSON message.";
    }

    @Override
    public String rootCause() {
        return "Jackson cannot construct target event class due to missing creator or default constructor.";
    }

    @Override
    public String recommendation() {
        return "Add a default constructor or annotate a constructor with @JsonCreator and @JsonProperty.";
    }
}
