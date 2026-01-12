package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.*;
import io.github.mathias82.logdoctor.incidents.JavaTimeSerializationIncident;

import java.util.Optional;

public class JavaTimeSerializationRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("LocalDateTime")
                && log.contains("No serializer found")) {

            JavaTimeSerializationIncident incident =
                    new JavaTimeSerializationIncident();

            incident.setEvidence(log);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
