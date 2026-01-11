package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;

import java.util.Optional;

public interface IncidentRule {
    Optional<Incident> match(RuleContext context);
}
