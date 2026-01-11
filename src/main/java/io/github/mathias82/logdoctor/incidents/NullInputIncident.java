package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class NullInputIncident extends Incident {

    @Override
    public String type() {
        return "Null Input Value";
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
        return "A required input value was null and caused a NullPointerException.";
    }

    @Override
    public String rootCause() {
        return "Missing validation for required input before business logic execution.";
    }

    @Override
    public String recommendation() {
        return """
        Validate required inputs explicitly before use.
        Add defensive null checks at service boundary.
        Do not rely on implicit assumptions or downstream failures.
        """;
    }
}
