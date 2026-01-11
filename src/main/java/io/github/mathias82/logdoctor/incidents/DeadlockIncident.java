package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class DeadlockIncident extends Incident {

    @Override
    public String type() {
        return "JVM Deadlock Detected";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.THREADING;
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
        return "A JVM-level deadlock was detected between multiple threads.";
    }

    @Override
    public String rootCause() {
        return "Two or more threads are waiting on locks held by each other, causing a permanent deadlock.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Capturing thread dumps (jstack)
        - Identifying lock ordering violations
        - Avoiding nested synchronized blocks
        - Using java.util.concurrent locks
        """;
    }
}
