package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.OutOfMemoryIncident;

import java.util.Optional;

public class OutOfMemoryRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("OutOfMemoryError")
                || log.contains("Java heap space")
                || log.contains("Metaspace")) {

            OutOfMemoryIncident incident =
                    new OutOfMemoryIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("OutOfMemoryError")
                                || l.contains("Java heap space")
                                || l.contains("Metaspace"))
                .findFirst()
                .orElse("JVM OutOfMemoryError detected");
    }
}
