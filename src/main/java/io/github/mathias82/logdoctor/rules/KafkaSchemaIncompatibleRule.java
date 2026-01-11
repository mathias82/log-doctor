package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.KafkaSchemaIncompatibleIncident;

import java.util.Optional;

public class KafkaSchemaIncompatibleRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("SchemaRegistry")
                && log.contains("incompatible")) {

            KafkaSchemaIncompatibleIncident incident =
                    new KafkaSchemaIncompatibleIncident();

            String evidence = log.lines()
                    .filter(l -> l.contains("SchemaRegistry"))
                    .findFirst()
                    .orElse("Kafka schema incompatibility detected");

            incident.setEvidence(evidence);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
