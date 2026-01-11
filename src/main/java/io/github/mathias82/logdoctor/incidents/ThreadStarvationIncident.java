package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class ThreadStarvationIncident extends Incident {

    @Override
    public String type() {
        return "Thread Starvation / Blocked Threads";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.THREADING;
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
        return "Application threads are blocked or exhausted.";
    }

    @Override
    public String rootCause() {
        return "Thread pools are saturated or threads are blocked by locks or I/O.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Reviewing thread pool sizing
        - Avoiding blocking calls
        - Analyzing thread dumps
        - Reducing lock contention
        """;
    }
}
