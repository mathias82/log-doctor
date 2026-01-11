package io.github.mathias82.logdoctor.core;

public abstract class Incident {

    public String evidence;
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

    public void setComponent(String component) {
        this.component = component;
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
                component != null ? component : "Unknown",
                summary(),
                rootCause(),
                recommendation(),
                evidence
        );
    }
}
