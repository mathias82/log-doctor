package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.NullPointerExceptionIncident;

import java.util.Optional;

public class NullPointerExceptionRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("NullPointerException")
                && log.contains("returned null")) {

            NullPointerExceptionIncident incident =
                    new NullPointerExceptionIncident();

            incident.setEvidence(log);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
