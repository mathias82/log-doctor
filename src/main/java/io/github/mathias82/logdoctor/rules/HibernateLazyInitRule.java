package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.HibernateLazyInitIncident;

import java.util.Optional;

public class HibernateLazyInitRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("LazyInitializationException")
                && log.contains("could not initialize proxy")) {

            HibernateLazyInitIncident incident =
                    new HibernateLazyInitIncident();

            incident.setEvidence(extractRelevantLines(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractRelevantLines(String log) {
        return log.lines()
                .filter(l -> l.contains("LazyInitializationException"))
                .findFirst()
                .orElse("LazyInitializationException detected");
    }
}
