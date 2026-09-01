package io.github.mathias82.logdoctor.llm;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.engine.LogRedactor;

import java.util.Objects;

/** Ensures common secrets and identifiers are redacted before log context reaches an LLM. */
public final class RedactingLlmClient implements LlmClient {
    private final LlmClient delegate;
    private final LogRedactor redactor;

    public RedactingLlmClient(LlmClient delegate) {
        this(delegate, new LogRedactor());
    }

    RedactingLlmClient(LlmClient delegate, LogRedactor redactor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.redactor = Objects.requireNonNull(redactor);
    }

    @Override
    public String explainKnownIncident(Incident incident) {
        String evidence = incident.evidence();
        String component = incident.component();
        try {
            incident.setEvidence(redactor.redact(evidence));
            incident.setComponent(redactor.redact(component));
            return delegate.explainKnownIncident(incident);
        } finally {
            incident.setEvidence(evidence);
            incident.setComponent(component);
        }
    }

    @Override
    public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
        return delegate.analyzeUnknownLog(redactor.redact(rawLog), category);
    }
}
