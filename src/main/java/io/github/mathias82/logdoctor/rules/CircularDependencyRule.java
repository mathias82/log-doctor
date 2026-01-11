package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.CircularDependencyIncident;

import java.util.Optional;

public class CircularDependencyRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("BeanCurrentlyInCreationException")
                || log.contains("Requested bean is currently in creation")) {

            CircularDependencyIncident incident =
                    new CircularDependencyIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l -> l.contains("BeanCurrentlyInCreationException"))
                .findFirst()
                .orElse("Circular dependency detected during Spring bean initialization");
    }
}
