package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.GcThrashingIncident;

import java.util.Optional;

public class GcThrashingRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("GC overhead limit exceeded")
                || log.contains("Pause Full")
                || log.contains("Full GC")) {

            GcThrashingIncident incident = new GcThrashingIncident();
            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("GC overhead")
                                || l.contains("Full GC"))
                .findFirst()
                .orElse("Excessive GC activity detected");
    }
}
