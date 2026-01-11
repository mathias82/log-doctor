package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.SpringConfigBindIncident;

import java.util.Optional;

public class SpringConfigBindRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("Failed to bind properties")
                || log.contains("ConfigurationPropertiesBindException")
                || log.contains("Could not bind properties")
                || log.contains("Could not resolve placeholder")) {

            SpringConfigBindIncident incident =
                    new SpringConfigBindIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("Failed to bind")
                                || l.contains("Could not resolve placeholder")
                                || l.contains("ConfigurationPropertiesBindException"))
                .findFirst()
                .orElse("Spring configuration binding error detected");
    }
}
