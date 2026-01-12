package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class UnsupportedOperationIncident extends Incident {

    public String type() { return "UnsupportedOperationException"; }
    public IncidentCategory category() { return IncidentCategory.TECHNICAL; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Unsupported operation invoked."; }
    public String rootCause() { return "Immutable collection modification."; }
    public String recommendation() { return "Avoid modifying immutable collections."; }
}