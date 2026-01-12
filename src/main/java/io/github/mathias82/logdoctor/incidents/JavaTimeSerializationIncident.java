package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class JavaTimeSerializationIncident extends Incident {

    @Override
    public String type() {
        return "Jackson Java Time Serialization Failure";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.DESERIALIZATION;
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public Confidence confidence() {
        return Confidence.HIGH;
    }

    @Override
    public String summary() {
        return "Jackson cannot serialize or deserialize java.time types.";
    }

    @Override
    public String rootCause() {
        return "Jackson JavaTimeModule is missing from ObjectMapper configuration.";
    }

    @Override
    public String recommendation() {
        return "Register JavaTimeModule in Jackson configuration.";
    }
}
