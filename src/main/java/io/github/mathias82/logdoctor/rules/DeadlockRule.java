package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.DeadlockIncident;

import java.util.Optional;

public class DeadlockRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        boolean deadlockDetected =
                log.contains("Found one Java-level deadlock")
                        || (log.contains("waiting to lock") && log.contains("BLOCKED"));

        if (deadlockDetected) {
            DeadlockIncident incident = new DeadlockIncident();
            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("Found one Java-level deadlock")
                                || l.contains("waiting to lock"))
                .findFirst()
                .orElse("Deadlock detected via thread dump");
    }
}
