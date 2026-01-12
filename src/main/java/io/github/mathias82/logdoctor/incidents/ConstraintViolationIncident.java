package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class ConstraintViolationIncident extends Incident {

    public String type() { return "ConstraintViolationException"; }
    public IncidentCategory category() { return IncidentCategory.CONFIGURATION; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Validation constraint violated."; }
    public String rootCause() { return "Invalid field value."; }
    public String recommendation() { return "Correct field value."; }
}