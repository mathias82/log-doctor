package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class GcThrashingIncident extends Incident {

    @Override
    public String type() {
        return "JVM GC Thrashing / GC Overhead";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.MEMORY;
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public Confidence confidence() {
        return Confidence.MEDIUM;
    }

    @Override
    public String summary() {
        return "The JVM spends excessive time performing garbage collection.";
    }

    @Override
    public String rootCause() {
        return "Heap pressure causes frequent full GCs with minimal memory reclaimed.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Increasing heap size (-Xmx)
        - Reducing allocation rate
        - Tuning GC (G1 / ZGC)
        - Analyzing GC logs
        """;
    }
}
