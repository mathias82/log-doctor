package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class OutOfMemoryIncident extends Incident {

    @Override
    public String type() {
        return "JVM OutOfMemoryError";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.MEMORY;
    }

    @Override
    public Severity severity() {
        return Severity.CRITICAL;
    }

    @Override
    public Confidence confidence() {
        return Confidence.HIGH;
    }

    @Override
    public String summary() {
        return "The JVM ran out of memory and could not allocate objects.";
    }

    @Override
    public String rootCause() {
        return "Memory usage exceeded heap, metaspace, or native memory limits.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Capturing heap dump
        - Analyzing memory leaks
        - Increasing JVM memory limits
        - Verifying container memory vs JVM settings
        """;
    }
}
