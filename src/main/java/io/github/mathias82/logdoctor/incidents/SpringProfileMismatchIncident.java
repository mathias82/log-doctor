package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class SpringProfileMismatchIncident extends Incident {

    @Override
    public String type() {
        return "Spring Profile / Bean Mismatch";
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
        return Confidence.MEDIUM;
    }

    @Override
    public String summary() {
        return "A required bean was not loaded due to profile mismatch.";
    }

    @Override
    public String rootCause() {
        return "Bean @Profile does not match the active Spring profile.";
    }

    @Override
    public String recommendation() {
        return """
        Fix by:
        - Verifying spring.profiles.active
        - Aligning @Profile annotations
        - Providing default beans
        """;
    }
}
