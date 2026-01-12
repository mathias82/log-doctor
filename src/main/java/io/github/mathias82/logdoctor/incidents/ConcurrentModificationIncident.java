package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class ConcurrentModificationIncident extends Incident {

    public String type() { return "ConcurrentModificationException"; }
    public IncidentCategory category() { return IncidentCategory.THREADING; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Concurrent modification detected."; }
    public String rootCause() { return "Collection modified during iteration."; }
    public String recommendation() { return "Avoid modifying during iteration."; }
}