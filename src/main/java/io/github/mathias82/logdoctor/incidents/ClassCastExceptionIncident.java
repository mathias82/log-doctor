package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class ClassCastExceptionIncident extends Incident {

    public String type() { return "ClassCastException"; }
    public IncidentCategory category() { return IncidentCategory.TECHNICAL; }
    public Severity severity() { return Severity.MEDIUM; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Invalid type cast."; }
    public String rootCause() { return "Object cast to incompatible type."; }
    public String recommendation() { return "Check object type before casting."; }
}