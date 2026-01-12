package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class NullPointerExceptionIncident extends Incident {

    @Override
    public String type() {
        return "NullPointerException";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.TECHNICAL;
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public Confidence confidence() {
        return Confidence.HIGH;
    }

    @Override
    public String summary() {
        return "Deterministic NullPointerException caused by null return value.";
    }

    @Override
    public String rootCause() {
        return "A method call returned null and was used without a null check.";
    }

    @Override
    public String recommendation() {
        return "Add a fail-fast null check using Objects.requireNonNull or Optional.orElseThrow.";
    }
}
