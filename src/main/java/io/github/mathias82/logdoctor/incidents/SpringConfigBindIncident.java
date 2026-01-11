package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class SpringConfigBindIncident extends Incident {

    @Override
    public String type() {
        return "Spring Boot Configuration Binding Failure";
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
        return "Spring Boot failed to bind configuration properties at startup.";
    }

    @Override
    public String rootCause() {
        return "A required configuration property is missing or malformed.";
    }

    @Override
    public String recommendation() {
        return """
        Verify application.yml / properties:
        - missing values
        - incorrect names
        - unresolved placeholders
        - active Spring profiles
        """;
    }
}
