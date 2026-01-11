package io.github.mathias82.logdoctor.core.rules;

import java.util.List;

public final class RuleCatalog {
    private RuleCatalog() {}

    public static List<Rule> defaultRules() {
        return List.of(
                new HibernateMissingTableRule()
                // add Kafka rules, Spring rules, JVM OOM rules, etc
        );
    }
}
