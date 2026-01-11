package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.rules.*;

import java.util.List;
import java.util.Optional;

public class IncidentDetector {

    private final List<IncidentRule> rules = List.of(
            new HibernateLazyInitRule(),
            new KafkaSchemaIncompatibleRule(),
            new HikariTimeoutRule(),
            new SpringConfigBindRule(),
            new SpringProfileMismatchRule(),
            new KafkaRebalanceLoopRule(),
            new CircularDependencyRule(),
            new OutOfMemoryRule(),
            new ThreadStarvationRule(),
            new GcThrashingRule(),
            new DeadlockRule(),
            new KafkaTopicNotFoundRule()
    );

    public Optional<Incident> detect(RuleContext context) {
        return rules.stream()
                .map(rule -> rule.match(context))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
