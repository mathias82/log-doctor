package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class KafkaTopicNotFoundIncident extends Incident {

    @Override
    public String type() {
        return "Kafka Topic Not Found";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.INFRASTRUCTURE;
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
        return "Kafka producer failed because the target topic does not exist or is not visible.";
    }

    @Override
    public String rootCause() {
        return "Kafka could not find topic metadata within the configured timeout.";
    }

    @Override
    public String recommendation() {
        return """
        Create the Kafka topic explicitly or verify:
        - Correct cluster (bootstrap.servers)
        - Topic existence
        - ACL permissions
        """;
    }
}
