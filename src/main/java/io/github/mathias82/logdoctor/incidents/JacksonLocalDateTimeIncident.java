package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class JacksonLocalDateTimeIncident extends Incident {

    @Override
    public String type() {
        return "Jackson LocalDateTime Serialization Failure";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.CONFIGURATION;
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
        return "Jackson cannot serialize java.time.LocalDateTime.";
    }

    @Override
    public String rootCause() {
        return "JavaTimeModule is missing from Jackson ObjectMapper.";
    }

    @Override
    public String recommendation() {
        return "Register JavaTimeModule in ObjectMapper configuration.";
    }
}
