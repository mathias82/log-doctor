package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class IndexOutOfBoundsIncident extends Incident {

    @Override
    public String type() {
        return "IndexOutOfBoundsException";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.TECHNICAL;
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public Confidence confidence() {
        return Confidence.HIGH;
    }

    @Override
    public String summary() {
        return "Index accessed outside valid bounds of a collection or array.";
    }

    @Override
    public String rootCause() {
        return "Code attempts to access an index that is negative or exceeds the collection size.";
    }

    @Override
    public String recommendation() {
        return "Validate index bounds before access using a fail-fast check.";
    }
}

