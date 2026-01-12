package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.*;
import io.github.mathias82.logdoctor.incidents.MissingSpringBeanIncident;

import java.util.Optional;

public class MissingSpringBeanRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("required a bean of type")
                && log.contains("could not be found")
                && log.contains("constructor")) {

            MissingSpringBeanIncident incident =
                    new MissingSpringBeanIncident();

            incident.setEvidence(log);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
