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
        return "A NullPointerException was detected in the failure context.";
    }

    @Override
    public String rootCause() {
        return "A null reference was dereferenced; use the first relevant application frame and surrounding evidence to identify where the unexpected null originated.";
    }

    @Override
    public String recommendation() {
        return "Inspect the first application-owned stack frame and the value source, then add validation or a fail-fast non-null contract where the invariant requires it. Avoid adding blind null checks that hide the underlying data or control-flow problem.";
    }
}
