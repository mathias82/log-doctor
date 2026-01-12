package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.*;

public class MissingSpringBeanIncident extends Incident {

    @Override
    public String type() {
        return "Missing Spring Bean";
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
        return "A required Spring bean is missing for the active profile.";
    }

    @Override
    public String rootCause() {
        return "Spring could not create a required dependency because no matching bean was defined.";
    }

    @Override
    public String recommendation() {
        return "Define the missing bean or activate the correct Spring profile.";
    }
}
