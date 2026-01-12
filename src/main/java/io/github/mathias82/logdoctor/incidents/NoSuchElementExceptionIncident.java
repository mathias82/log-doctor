package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class NoSuchElementExceptionIncident extends Incident {

    public String type() { return "NoSuchElementException"; }
    public IncidentCategory category() { return IncidentCategory.TECHNICAL; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Accessed missing element."; }
    public String rootCause() { return "Optional.get() called without value."; }
    public String recommendation() { return "Use orElseThrow()."; }
}