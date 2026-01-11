package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.incidents.Incident;

public interface IncidentRule {

    boolean matches(String log);

    Incident createIncident(String log);
}
