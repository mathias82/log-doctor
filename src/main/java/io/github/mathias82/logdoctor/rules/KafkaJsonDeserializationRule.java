package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.*;
import io.github.mathias82.logdoctor.incidents.KafkaJsonDeserializationIncident;

import java.util.Optional;

public class KafkaJsonDeserializationRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("SerializationException")
                && log.contains("DeserializationException")
                && log.contains("InvalidDefinitionException")
                && log.contains("Cannot construct instance")) {

            KafkaJsonDeserializationIncident incident =
                    new KafkaJsonDeserializationIncident();

            incident.setEvidence(log);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
