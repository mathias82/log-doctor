package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class HttpMessageNotReadableIncident extends Incident {

    public String type() { return "HttpMessageNotReadableException"; }
    public IncidentCategory category() { return IncidentCategory.DESERIALIZATION; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Malformed HTTP request body."; }
    public String rootCause() { return "Invalid JSON or incompatible field."; }
    public String recommendation() { return "Fix JSON structure."; }
}