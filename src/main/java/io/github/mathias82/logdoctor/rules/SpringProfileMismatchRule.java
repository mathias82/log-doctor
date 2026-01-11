package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.SpringProfileMismatchIncident;

import java.util.Optional;

public class SpringProfileMismatchRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {

        String log = ctx.contextText();

        if (log.contains("No qualifying bean of type")
                || log.contains("NoSuchBeanDefinitionException")) {

            SpringProfileMismatchIncident incident =
                    new SpringProfileMismatchIncident();

            incident.setEvidence(extractEvidence(log));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    private String extractEvidence(String log) {
        return log.lines()
                .filter(l ->
                        l.contains("No qualifying bean")
                                || l.contains("NoSuchBeanDefinitionException"))
                .findFirst()
                .orElse("Spring bean resolution failure detected");
    }
}
