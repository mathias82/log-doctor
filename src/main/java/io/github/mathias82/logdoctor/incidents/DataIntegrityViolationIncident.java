package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class DataIntegrityViolationIncident extends Incident {

    public String type() { return "DataIntegrityViolationException"; }
    public IncidentCategory category() { return IncidentCategory.DATABASE; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Database constraint violated."; }
    public String rootCause() { return "Unique or FK constraint violation."; }
    public String recommendation() { return "Ensure data integrity."; }
}