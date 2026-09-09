package io.github.mathias82.logdoctor.engine;

import java.util.List;

public class FailingIncidentRuleProvider implements IncidentRuleProvider {

    @Override
    public List<IncidentRule> rules() {
        throw new IllegalStateException("provider exploded");
    }
}