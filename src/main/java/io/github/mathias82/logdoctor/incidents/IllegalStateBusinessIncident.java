package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class IllegalStateBusinessIncident extends Incident {

    @Override
    public String type() {
        return "Invalid Business State Transition";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.BUSINESS;
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
        return "A business operation was attempted in an invalid domain state.";
    }

    @Override
    public String rootCause() {
        return "Domain state machine rejected an illegal state transition.";
    }

    @Override
    public String recommendation() {
        return """
        This is a business rule violation.
        Verify client behavior, API contract, and domain state transitions.
        No automatic fix can be safely applied.
        """;
    }
}
