package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class CircularDependencyIncident extends Incident {

    @Override
    public String type() {
        return "Spring Circular Dependency Detected";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.CONFIGURATION;
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
        return "Spring failed to start due to a circular dependency between beans.";
    }

    @Override
    public String rootCause() {
        return "Two or more Spring beans depend on each other directly or indirectly during initialization.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Using constructor injection instead of field injection
        - Introducing an interface to break the cycle
        - Refactoring shared logic into a third component
        - Using @Lazy only as a temporary workaround
        """;
    }
}
