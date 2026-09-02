package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;

/**
 * Data-driven incident used by the common failure catalog.
 */
public final class CatalogIncident extends Incident {

    private final String type;
    private final IncidentCategory category;
    private final Severity severity;
    private final Confidence confidence;
    private final String summary;
    private final String rootCause;
    private final String recommendation;

    public CatalogIncident(
            String type,
            IncidentCategory category,
            Severity severity,
            Confidence confidence,
            String component,
            String summary,
            String rootCause,
            String recommendation
    ) {
        this.type = type;
        this.category = category;
        this.severity = severity;
        this.confidence = confidence;
        this.summary = summary;
        this.rootCause = rootCause;
        this.recommendation = recommendation;
        setComponent(component);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public IncidentCategory category() {
        return category;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public Confidence confidence() {
        return confidence;
    }

    @Override
    public String summary() {
        return summary;
    }

    @Override
    public String rootCause() {
        return rootCause;
    }

    @Override
    public String recommendation() {
        return recommendation;
    }
}
