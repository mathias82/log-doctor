package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.IllegalStateBusinessIncident;

import java.util.Optional;

public class IllegalStateBusinessRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        String log = ctx.contextText();

        if (log.contains("IllegalStateException")
                && log.contains("Cannot cancel order")) {

            IllegalStateBusinessIncident incident =
                    new IllegalStateBusinessIncident();

            incident.setEvidence(log);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
