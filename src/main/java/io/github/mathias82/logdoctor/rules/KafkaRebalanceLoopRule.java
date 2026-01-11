package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.KafkaRebalanceLoopIncident;

import java.util.Optional;

public class KafkaRebalanceLoopRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("Revoking previously assigned partitions")
                && log.contains("Rebalance")) {

            KafkaRebalanceLoopIncident incident =
                    new KafkaRebalanceLoopIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("Rebalance")
                                || l.contains("Revoking previously assigned partitions"))
                .findFirst()
                .orElse("Kafka consumer rebalance detected");
    }
}
