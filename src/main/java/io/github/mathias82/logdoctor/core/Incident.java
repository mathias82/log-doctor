package io.github.mathias82.logdoctor.core;

public abstract class Incident {

    private String evidence;
    private String component;

    public abstract String type();
    public abstract IncidentCategory category();
    public abstract Severity severity();
    public abstract Confidence confidence();

    public abstract String summary();
    public abstract String rootCause();
    public abstract String recommendation();

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String evidence() {
        return evidence;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String component() {
        return component != null ? component : "Unknown";
    }

    public String format() {
        return """
        🚨 %s

        Category: %s
        Severity: %s
        Confidence: %s

        WHERE:
        %s

        Summary:
        %s

        Root cause:
        %s

        Recommendation:
        %s

        Evidence:
        %s
        """.formatted(
                type(),
                category(),
                severity(),
                confidence(),
                component(),
                summary(),
                rootCause(),
                recommendation(),
                evidence
        );
    }
}
