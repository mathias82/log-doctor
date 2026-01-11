package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.ThreadStarvationIncident;

import java.util.Optional;

public class ThreadStarvationRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("thread starvation")
                || log.contains("Blocked")
                || log.contains("waiting to lock")
                || log.contains("Timed out waiting for")) {

            ThreadStarvationIncident incident =
                    new ThreadStarvationIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("Blocked")
                                || l.contains("waiting to lock")
                                || l.contains("thread starvation"))
                .findFirst()
                .orElse("Thread starvation or blocking detected");
    }
}
