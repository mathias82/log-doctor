package io.github.mathias82.logdoctor.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mathias82.logdoctor.core.RemediationMetadata;
import io.github.mathias82.logdoctor.engine.CauseChainAnalyzer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.engine.LogRedactor;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral, redacted diagnostic output intended to be consumed by coding agents.
 *
 * <p>This formatter does not add diagnosis policy. It only projects backend-owned
 * diagnosis/evidence/remediation data into a stable agent-facing envelope.</p>
 */
final class AgentOutputFormatter {
    static final String CONTRACT_VERSION = "1";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LogRedactor REDACTOR = new LogRedactor();

    private AgentOutputFormatter() {}

    static String agent(DiagnosisEngine.DiagnosisResult result, Path source) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contractVersion", CONTRACT_VERSION);
        envelope.put("producer", Map.of(
                "name", "Log Doctor",
                "output", "agent"));
        envelope.put("source", source(result, source));
        envelope.put("analysis", analysis(result));
        envelope.put("incident", incident(result));
        envelope.put("evidence", evidence(result));
        envelope.put("rootCauseCandidates", rootCauseCandidates(result));
        envelope.put("investigation", investigation(result.remediation()));
        envelope.put("safety", safety(result));
        return pretty(envelope);
    }

    private static Map<String, Object> source(DiagnosisEngine.DiagnosisResult result, Path source) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("path", source.toString().replace('\\', '/'));
        if (result.failureLine() != null && result.failureLine() > 0) {
            value.put("failureLine", result.failureLine());
        }
        return value;
    }

    private static Map<String, Object> analysis(DiagnosisEngine.DiagnosisResult result) {
        return Map.of(
                "status", safe(result.status()),
                "source", result.llmUsed() ? "LOCAL_LLM_ASSISTED" : "DETERMINISTIC_OR_FALLBACK",
                "llmUsed", result.llmUsed());
    }

    private static Map<String, Object> incident(DiagnosisEngine.DiagnosisResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", safe(result.type()));
        value.put("category", safe(result.category()));
        value.put("severity", safe(result.severity()));
        value.put("confidence", safe(result.confidence()));
        value.put("summary", redact(result.summary()));
        value.put("location", redact(result.location()));
        value.put("matchScore", result.matchScore());
        value.put("matchConfidence", safe(result.matchConfidence()));
        return value;
    }

    private static Map<String, Object> evidence(DiagnosisEngine.DiagnosisResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("excerpt", redact(result.evidence()));
        value.put("whyMatched", result.matchReasons() == null
                ? List.of()
                : result.matchReasons().stream().map(AgentOutputFormatter::redact).toList());
        value.put("matchScoreFactors", result.matchScoreFactors() == null
                ? List.of()
                : result.matchScoreFactors().stream().map(AgentOutputFormatter::redact).toList());
        value.put("causeChain", causeChain(result.causeChain()));
        return value;
    }

    private static List<Map<String, Object>> causeChain(List<CauseChainAnalyzer.Cause> causes) {
        if (causes == null || causes.isEmpty()) {
            return List.of();
        }
        return causes.stream().map(cause -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("line", cause.lineNumber());
            value.put("exceptionType", safe(cause.exceptionType()));
            value.put("message", redact(cause.message()));
            value.put("evidence", redact(cause.evidence()));
            return value;
        }).toList();
    }

    private static List<String> rootCauseCandidates(DiagnosisEngine.DiagnosisResult result) {
        String rootCause = redact(result.rootCause());
        return rootCause.isBlank() || "—".equals(rootCause) ? List.of() : List.of(rootCause);
    }

    private static Map<String, Object> investigation(RemediationMetadata remediation) {
        if (remediation == null) {
            return Map.of(
                    "allowedActions", List.of(),
                    "verificationSteps", List.of());
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("allowedActions", remediation.allowedActions());
        value.put("verificationSteps", remediation.verificationSteps());
        value.put("playbook", remediation.playbook());
        return value;
    }

    private static Map<String, Object> safety(DiagnosisEngine.DiagnosisResult result) {
        boolean automaticExecutionAllowed = result.remediation() != null
                && result.remediation().automaticExecutionAllowed();
        return Map.of(
                "automaticExecutionAllowed", automaticExecutionAllowed,
                "humanReviewRequired", result.humanReviewRequired(),
                "redactionAppliedToAgentEvidence", true);
    }

    private static String pretty(Object value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent diagnosis", e);
        }
    }

    private static String redact(String value) {
        return safe(REDACTOR.redact(value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
