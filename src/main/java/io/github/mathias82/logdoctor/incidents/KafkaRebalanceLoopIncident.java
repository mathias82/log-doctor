package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class KafkaRebalanceLoopIncident extends Incident {

    @Override
    public String type() {
        return "Kafka Consumer Rebalance Loop";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.INFRASTRUCTURE;
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public Confidence confidence() {
        return Confidence.MEDIUM;
    }

    @Override
    public String summary() {
        return "Kafka consumer is repeatedly rebalancing and not making progress.";
    }

    @Override
    public String rootCause() {
        return "Consumer processing exceeds max.poll.interval.ms or heartbeats are delayed.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Increasing max.poll.interval.ms
        - Reducing max.poll.records
        - Offloading long processing from the poll thread
        """;
    }
}
