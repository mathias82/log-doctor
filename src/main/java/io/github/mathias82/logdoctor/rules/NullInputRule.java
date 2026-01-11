package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.NullInputIncident;

import java.util.Optional;

public class NullInputRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("NullPointerException")
                && log.contains("Cannot invoke")
                && log.contains("is null")) {

            NullInputIncident incident = new NullInputIncident();
            incident.setEvidence(extractEvidence(log));

            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l -> l.contains("NullPointerException")
                        || l.contains("Cannot invoke"))
                .findFirst()
                .orElse("Null input caused NullPointerException");
    }
}
