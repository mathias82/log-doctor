package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class HibernateLazyInitIncident extends Incident {

    @Override
    public String type() {
        return "Hibernate LazyInitializationException";
    }

    @Override
    public IncidentCategory category() {
        return IncidentCategory.DATABASE;
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
        return "A lazy-loaded Hibernate association was accessed outside of a session.";
    }

    @Override
    public String rootCause() {
        return "The entity was detached when a lazy relationship was accessed.";
    }

    @Override
    public String recommendation() {
        return "Access the association within a transaction or use JOIN FETCH / DTO projections.";
    }
}
