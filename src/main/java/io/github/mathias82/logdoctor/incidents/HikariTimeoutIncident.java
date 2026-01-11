package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class HikariTimeoutIncident extends Incident {

    @Override
    public String type() {
        return "HikariCP Connection Timeout";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.DATABASE;
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
        return "The database connection pool was exhausted.";
    }

    @Override
    public String rootCause() {
        return "Connections were not returned to the pool or pool size is insufficient.";
    }

    @Override
    public String recommendation() {
        return "Check for connection leaks and tune HikariCP pool settings.";
    }
}
