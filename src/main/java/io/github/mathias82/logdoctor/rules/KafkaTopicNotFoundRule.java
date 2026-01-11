package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.*;
import io.github.mathias82.logdoctor.incidents.KafkaTopicNotFoundIncident;

import java.util.Optional;

public class KafkaTopicNotFoundRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("not present in metadata")
                && log.contains("Topic")
                && log.contains("TimeoutException")) {

            KafkaTopicNotFoundIncident incident =
                    new KafkaTopicNotFoundIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l -> l.contains("not present in metadata"))
                .findFirst()
                .orElse("Kafka topic not present in metadata");
    }
}
