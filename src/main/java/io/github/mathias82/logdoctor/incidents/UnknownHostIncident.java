package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class UnknownHostIncident extends Incident {

    public String type() { return "UnknownHostException"; }
    public IncidentCategory category() { return IncidentCategory.INFRASTRUCTURE; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Host could not be resolved."; }
    public String rootCause() { return "DNS misconfiguration."; }
    public String recommendation() { return "Verify hostname/DNS."; }
}