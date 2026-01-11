package io.github.mathias82.logdoctor.core.rules;

import io.github.mathias82.logdoctor.core.analysis.Severity;
import io.github.mathias82.logdoctor.incidents.Incident;

public class HibernateLazyInitializationIncident implements Incident {

    @Override
    public String id() {
        return "HIBERNATE_LAZY_INIT";
    }

    @Override
    public String title() {
        return "Hibernate LazyInitializationException";
    }

    @Override
    public String description() {
        return "A lazy-loaded association was accessed outside of an active Hibernate session.";
    }

    @Override
    public String rootCause() {
        return "The entity relationship was marked as LAZY and accessed after the session was closed.";
    }

    @Override
    public String recommendation() {
        return """
        Possible fixes:
        - Use fetch join in your query
        - Initialize the association within a transactional boundary
        - Change fetch type to EAGER (with caution)
        - Map DTOs instead of exposing entities
        """;
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }
}
