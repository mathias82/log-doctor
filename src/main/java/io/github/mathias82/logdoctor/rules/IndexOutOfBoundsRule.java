package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.IndexOutOfBoundsIncident;

import java.util.Optional;

public class IndexOutOfBoundsRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if ((log.contains("IndexOutOfBoundsException")
                || log.contains("ArrayIndexOutOfBoundsException"))
                && (log.contains("out of bounds")
                || log.contains("Index"))) {

            IndexOutOfBoundsIncident incident =
                    new IndexOutOfBoundsIncident();

            incident.setEvidence(log);
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
