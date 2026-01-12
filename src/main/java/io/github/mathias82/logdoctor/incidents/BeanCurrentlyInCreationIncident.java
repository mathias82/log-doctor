package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class BeanCurrentlyInCreationIncident extends Incident {

    public String type() { return "BeanCurrentlyInCreationException"; }
    public IncidentCategory category() { return IncidentCategory.CONFIGURATION; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Circular dependency between Spring beans."; }
    public String rootCause() { return "Beans depend on each other during initialization."; }
    public String recommendation() { return "Refactor to break circular dependency or use setter injection."; }
}
