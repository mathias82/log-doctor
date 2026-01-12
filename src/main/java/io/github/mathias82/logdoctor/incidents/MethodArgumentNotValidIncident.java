package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class MethodArgumentNotValidIncident extends Incident {

    public String type() { return "MethodArgumentNotValidException"; }
    public IncidentCategory category() { return IncidentCategory.CONFIGURATION; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Request validation failed."; }
    public String rootCause() { return "DTO validation constraints violated."; }
    public String recommendation() { return "Fix request payload."; }
}