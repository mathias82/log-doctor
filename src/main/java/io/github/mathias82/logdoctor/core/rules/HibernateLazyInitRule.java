package io.github.mathias82.logdoctor.core.rules;

import io.github.mathias82.logdoctor.incidents.Incident;
import io.github.mathias82.logdoctor.incidents.IncidentRule;

public class HibernateLazyInitRule implements IncidentRule {

    @Override
    public boolean matches(String log) {
        return log.contains("LazyInitializationException")
                && log.contains("could not initialize proxy")
                && log.contains("no Session");
    }

    @Override
    public Incident createIncident(String log) {
        return new HibernateLazyInitializationIncident();
    }
}
