package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Produces an auditable 0-100 score for deterministic rule matches.
 *
 * <p>The score is not a probability. It is a deterministic evidence-strength
 * indicator based on rule specificity and evidence visible in the same failure
 * context.</p>
 */
public final class MatchConfidenceScorer {

    private static final int BASE_RULE_MATCH = 55;
    private static final int EVIDENCE_PRESENT = 20;
    private static final int CAUSE_CHAIN_MATCH = 15;
    private static final int SPECIALIZED_RULE = 10;

    public Score score(
            IncidentDetector.Detection detection,
            List<CauseChainAnalyzer.Cause> causeChain,
            String contextText
    ) {
        Incident incident = detection.incident();
        int value = BASE_RULE_MATCH;
        List<String> factors = new ArrayList<>();
        factors.add("Deterministic rule matched (+" + BASE_RULE_MATCH + ")");

        if (incident.evidence() != null && !incident.evidence().isBlank()) {
            value += EVIDENCE_PRESENT;
            factors.add("Matching evidence was extracted (+" + EVIDENCE_PRESENT + ")");
        }

        if (causeChainContainsIncident(causeChain, incident.type())) {
            value += CAUSE_CHAIN_MATCH;
            factors.add("Incident type appears in the visible cause chain (+" + CAUSE_CHAIN_MATCH + ")");
        }

        if (!"CommonFailureCatalogRule".equals(detection.rule())) {
            value += SPECIALIZED_RULE;
            factors.add("Specialized rule matched before the broad catalog (+" + SPECIALIZED_RULE + ")");
        }

        int bounded = Math.min(100, value);
        return new Score(bounded, band(bounded), List.copyOf(factors));
    }

    public Score protectedFallback(String reason) {
        return new Score(
                95,
                "VERY_HIGH",
                List.of(
                        "Protected deterministic fallback matched (+70)",
                        reason + " (+25)"
                )
        );
    }

    public Score unknown() {
        return new Score(0, "NONE", List.of("No deterministic rule matched"));
    }

    private static boolean causeChainContainsIncident(
            List<CauseChainAnalyzer.Cause> causeChain,
            String incidentType
    ) {
        if (causeChain == null || causeChain.isEmpty() || incidentType == null || incidentType.isBlank()) {
            return false;
        }

        String expected = simpleName(incidentType);
        return causeChain.stream()
                .map(CauseChainAnalyzer.Cause::exceptionType)
                .map(MatchConfidenceScorer::simpleName)
                .anyMatch(expected::equals);
    }

    private static String simpleName(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private static String band(int value) {
        if (value >= 90) {
            return "VERY_HIGH";
        }
        if (value >= 75) {
            return "HIGH";
        }
        if (value >= 60) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public record Score(
            int value,
            String band,
            List<String> factors
    ) {}
}
