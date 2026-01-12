package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.*;
import io.github.mathias82.logdoctor.incidents.IllegalArgumentExceptionIncident;

import java.util.Optional;

public class IllegalArgumentExceptionRule implements IncidentRule {
    @Override
    public Optional<Incident> match(RuleContext ctx) {
        if (ctx.contextText().contains("IllegalArgumentException")) {
            IllegalArgumentExceptionIncident i = new IllegalArgumentExceptionIncident();
            i.setEvidence(ctx.contextText());
            return Optional.of(i);
        }
        return Optional.empty();
    }
}
