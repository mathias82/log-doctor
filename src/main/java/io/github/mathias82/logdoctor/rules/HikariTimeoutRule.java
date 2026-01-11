package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.HikariTimeoutIncident;

import java.util.Optional;

public class HikariTimeoutRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("HikariPool")
                && log.contains("Connection is not available")) {

            HikariTimeoutIncident incident = new HikariTimeoutIncident();

            String evidence = log.lines()
                    .filter(l -> l.contains("HikariPool"))
                    .findFirst()
                    .orElse("HikariPool connection timeout detected");

            incident.setEvidence(evidence);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
