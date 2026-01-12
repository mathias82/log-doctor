package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class AccessDeniedIncident extends Incident {

    public String type() { return "AccessDeniedException"; }
    public IncidentCategory category() { return IncidentCategory.SECURITY; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Access denied by security configuration."; }
    public String rootCause() { return "Missing or incorrect authorization rules."; }
    public String recommendation() { return "Review Spring Security configuration and roles."; }
}
