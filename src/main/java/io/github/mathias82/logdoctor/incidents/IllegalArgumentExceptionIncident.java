package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class IllegalArgumentExceptionIncident extends Incident {

    public String type() { return "IllegalArgumentException"; }
    public IncidentCategory category() { return IncidentCategory.TECHNICAL; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Illegal argument passed to method."; }
    public String rootCause() { return "Method received an invalid argument."; }
    public String recommendation() { return "Validate input arguments."; }
}