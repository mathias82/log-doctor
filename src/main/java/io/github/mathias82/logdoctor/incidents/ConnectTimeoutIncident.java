package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class ConnectTimeoutIncident extends Incident {

    public String type() { return "ConnectTimeoutException"; }
    public IncidentCategory category() { return IncidentCategory.INFRASTRUCTURE; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Connection timed out."; }
    public String rootCause() { return "Remote host unreachable."; }
    public String recommendation() { return "Check network/service."; }
}