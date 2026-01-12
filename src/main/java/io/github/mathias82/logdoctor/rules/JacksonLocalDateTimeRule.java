package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.*;
import io.github.mathias82.logdoctor.incidents.JacksonLocalDateTimeIncident;

import java.util.Optional;

public class JacksonLocalDateTimeRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        String log = ctx.contextText();

        if (log.contains("InvalidDefinitionException")
                && log.contains("LocalDateTime")
                && log.contains("jackson-datatype-jsr310")) {

            return Optional.of(new JacksonLocalDateTimeIncident());
        }

        return Optional.empty();
    }
}
