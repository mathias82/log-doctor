package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class TransactionRequiredIncident extends Incident {

    public String type() { return "TransactionRequiredException"; }
    public IncidentCategory category() { return IncidentCategory.DATABASE; }
    public Severity severity() { return Severity.HIGH; }
    public Confidence confidence() { return Confidence.HIGH; }

    public String summary() { return "Operation requires transaction."; }
    public String rootCause() { return "Missing transactional boundary."; }
    public String recommendation() { return "Add @Transactional."; }
}