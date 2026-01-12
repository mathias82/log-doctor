package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class NoSuchMethodErrorIncident extends Incident {

    public String type() { return "NoSuchMethodError"; }
    public IncidentCategory category() { return IncidentCategory.TECHNICAL; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Method not found at runtime."; }
    public String rootCause() { return "Binary incompatibility or dependency version conflict."; }
    public String recommendation() { return "Align dependency versions and rebuild."; }
}
