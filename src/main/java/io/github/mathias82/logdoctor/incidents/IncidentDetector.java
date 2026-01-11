package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.incidents.Incident;

import java.util.List;
import java.util.Optional;

public class IncidentDetector {

    private final List<IncidentRule> rules;

    public IncidentDetector(List<IncidentRule> rules) {
        this.rules = rules;
    }

    public Optional<Incident> detect(String logContent) {
        for (IncidentRule rule : rules) {
            if (rule.matches(logContent)) {
                return Optional.of(rule.createIncident(logContent));
            }
        }
        return Optional.empty();
    }
}
