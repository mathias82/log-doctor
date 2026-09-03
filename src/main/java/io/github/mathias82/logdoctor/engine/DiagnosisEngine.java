package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.FixPolicy;
import io.github.mathias82.logdoctor.core.FixType;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.RemediationMetadata;
import io.github.mathias82.logdoctor.llm.LlmClient;
import io.github.mathias82.logdoctor.llm.OllamaLlmClient;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates the structured diagnosis pipeline: locate the failure, extract context,
 * run deterministic rules, apply protected fallbacks, and use the optional local LLM
 * only when policy and the caller allow it.
 *
 * <p>The engine owns diagnosis semantics and safety metadata. Presentation layers
 * should consume {@link DiagnosisResult} rather than reimplementing policy.</p>
 */
public class DiagnosisEngine {
    private static final int CONTEXT_RADIUS = 8;
    private static final String NO_AUTOMATIC_FIX = "No safe automatic fix, human investigation required.";

    private final IncidentDetector detector;
    private final LogParser parser;
    private final FailureLocator failureLocator;
    private final FailureContextExtractor contextExtractor;
    private final CauseChainAnalyzer causeChainAnalyzer;
    private final MatchConfidenceScorer matchConfidenceScorer;
    private final LlmClient llm;

    public DiagnosisEngine() {
        this(new OllamaLlmClient());
    }

    public DiagnosisEngine(LlmClient llm) {
        this(
                new IncidentDetector(),
                new LogParser(),
                new FailureLocator(),
                new FailureContextExtractor(),
                new CauseChainAnalyzer(),
                new MatchConfidenceScorer(),
                llm);
    }

    DiagnosisEngine(
            IncidentDetector detector,
            LogParser parser,
            FailureLocator failureLocator,
            FailureContextExtractor contextExtractor,
            LlmClient llm
    ) {
        this(
                detector,
                parser,
                failureLocator,
                contextExtractor,
                new CauseChainAnalyzer(),
                new MatchConfidenceScorer(),
                llm);
    }

    DiagnosisEngine(
            IncidentDetector detector,
            LogParser parser,
            FailureLocator failureLocator,
            FailureContextExtractor contextExtractor,
            CauseChainAnalyzer causeChainAnalyzer,
            LlmClient llm
    ) {
        this(
                detector,
                parser,
                failureLocator,
                contextExtractor,
                causeChainAnalyzer,
                new MatchConfidenceScorer(),
                llm);
    }

    DiagnosisEngine(
            IncidentDetector detector,
            LogParser parser,
            FailureLocator failureLocator,
            FailureContextExtractor contextExtractor,
            CauseChainAnalyzer causeChainAnalyzer,
            MatchConfidenceScorer matchConfidenceScorer,
            LlmClient llm
    ) {
        this.detector = detector;
        this.parser = parser;
        this.failureLocator = failureLocator;
        this.contextExtractor = contextExtractor;
        this.causeChainAnalyzer = causeChainAnalyzer;
        this.matchConfidenceScorer = matchConfidenceScorer;
        this.llm = llm;
    }

    public void analyze(String log) {
        System.out.print(analyzeToText(log));
    }

    public String analyzeToText(String log) {
        return analyzeStructured(log).diagnosis();
    }

    public DiagnosisResult analyzeStructured(String log) {
        return analyzeStructured(log, true);
    }

    DiagnosisResult analyzeStructured(String log, boolean allowLlm) {
        if (log == null || log.isBlank()) {
            return DiagnosisResult.empty("No log content provided.");
        }

        var lines = parser.parse(log);
        var failureOpt = failureLocator.locate(lines);
        if (failureOpt.isEmpty()) {
            return DiagnosisResult.empty("No obvious failure found.");
        }

        var failure = failureOpt.get();
        String contextText = contextExtractor.extract(lines, failure, CONTEXT_RADIUS);
        String location = failure.blameLocation() != null
                ? failure.blameLocation().content()
                : failure.rootCause().content();
        List<CauseChainAnalyzer.Cause> chain = causeChainAnalyzer.analyze(lines);

        var detectionOpt = detector
                .detectDetailed(new RuleContext(lines, failure, contextText))
                .filter(detection -> detection.incident().confidence() == Confidence.HIGH);

        if (detectionOpt.isPresent()) {
            var detection = detectionOpt.get();
            return diagnosedIncident(
                    detection.incident(),
                    contextText,
                    location,
                    failure.rootCause().lineNumber(),
                    allowLlm,
                    chain,
                    detection.reasons(),
                    matchConfidenceScorer.score(detection, chain, contextText));
        }

        String lower = contextText.toLowerCase(Locale.ROOT);
        if (isConcurrencyFailure(lower)) {
            return manualReview(
                    failure.rootCause().lineNumber(),
                    location,
                    "CONCURRENCY_FAILURE",
                    IncidentCategory.THREADING,
                    "Concurrency / data consistency failure",
                    "Concurrency / data consistency failure detected in application layer",
                    contextText,
                    chain,
                    List.of(
                            "Matched protected concurrency fallback",
                            "Concurrency signature found in failure context"),
                    matchConfidenceScorer.protectedFallback(
                            "Concurrency signature found in failure context"));
        }

        if (isBusinessInvariantFailure(lower)) {
            return manualReview(
                    failure.rootCause().lineNumber(),
                    location,
                    "BUSINESS_INVARIANT",
                    IncidentCategory.BUSINESS,
                    "Domain state machine violation",
                    "Domain state machine / business invariant violation",
                    contextText,
                    chain,
                    List.of(
                            "Matched protected business-invariant fallback",
                            "IllegalStateException state/transition signature found"),
                    matchConfidenceScorer.protectedFallback(
                            "IllegalStateException state/transition signature found"));
        }

        String deepest = chain.isEmpty()
                ? failure.rootCause().content()
                : chain.get(chain.size() - 1).evidence();
        return unknownFailure(
                contextText,
                lower,
                location,
                deepest,
                failure.rootCause().lineNumber(),
                allowLlm,
                chain,
                matchConfidenceScorer.unknown());
    }

    private DiagnosisResult diagnosedIncident(
            Incident incident,
            String evidence,
            String location,
            int line,
            boolean allowLlm,
            List<CauseChainAnalyzer.Cause> chain,
            List<String> reasons,
            MatchConfidenceScorer.Score score
    ) {
        incident.setEvidence(evidence);
        incident.setComponent(location);

        Set<FixType> allowed = FixPolicy.allowedFixes(incident.category());
        boolean humanReviewRequired = allowed.contains(FixType.NO_AUTOMATIC_FIX);
        String fixType = humanReviewRequired
                ? FixType.NO_AUTOMATIC_FIX.name()
                : formatFixTypes(allowed);
        String fix = humanReviewRequired ? NO_AUTOMATIC_FIX : incident.recommendation();
        String llmAnalysis = !allowLlm || humanReviewRequired
                ? null
                : safelyExplainKnownIncident(incident);
        RemediationMetadata remediation = RemediationMetadata.from(incident, allowed);

        String diagnosis = incident.format()
                + System.lineSeparator()
                + formatCauseChain(chain)
                + formatMatchScore(score)
                + formatMatchReasons(reasons)
                + "FIX:"
                + System.lineSeparator()
                + fix
                + System.lineSeparator()
                + formatLlmSection(llmAnalysis);

        return new DiagnosisResult(
                "DIAGNOSED",
                incident.type(),
                incident.category().name(),
                incident.severity().name(),
                incident.confidence().name(),
                incident.component(),
                incident.summary(),
                incident.rootCause(),
                incident.evidence(),
                fixType,
                fix,
                humanReviewRequired,
                llmAnalysis != null,
                line,
                diagnosis,
                chain,
                reasons,
                score.value(),
                score.band(),
                score.factors(),
                remediation);
    }

    private DiagnosisResult unknownFailure(
            String context,
            String lower,
            String location,
            String rootCause,
            int line,
            boolean allowLlm,
            List<CauseChainAnalyzer.Cause> chain,
            MatchConfidenceScorer.Score score
    ) {
        IncidentCategory category = inferUnknownCategory(lower);
        String llmAnalysis = allowLlm ? safelyAnalyzeUnknownLog(context, category) : null;
        String fix = llmAnalysis == null
                ? "No deterministic rule matched and local LLM analysis is unavailable. Human review required."
                : "Review the local LLM analysis and supporting evidence.";
        List<String> reasons = List.of("No deterministic rule matched the failure context");

        String diagnosis = "Unknown failure detected at line "
                + line
                + System.lineSeparator()
                + context
                + System.lineSeparator()
                + formatCauseChain(chain)
                + formatMatchScore(score)
                + formatMatchReasons(reasons)
                + formatLlmSection(llmAnalysis);

        return new DiagnosisResult(
                "UNKNOWN",
                "UNKNOWN_FAILURE",
                category.name(),
                "UNKNOWN",
                "LOW",
                location,
                "No deterministic rule matched this failure.",
                rootCause,
                context,
                FixType.NO_AUTOMATIC_FIX.name(),
                fix,
                true,
                llmAnalysis != null,
                line,
                diagnosis,
                chain,
                reasons,
                score.value(),
                score.band(),
                score.factors(),
                RemediationMetadata.from(category, Set.of(FixType.NO_AUTOMATIC_FIX)));
    }

    private DiagnosisResult manualReview(
            int line,
            String location,
            String type,
            IncidentCategory category,
            String summary,
            String rootCause,
            String evidence,
            List<CauseChainAnalyzer.Cause> chain,
            List<String> reasons,
            MatchConfidenceScorer.Score score
    ) {
        String diagnosis = "WHERE:"
                + System.lineSeparator()
                + location
                + System.lineSeparator()
                + System.lineSeparator()
                + "ROOT CAUSE:"
                + System.lineSeparator()
                + rootCause
                + System.lineSeparator()
                + System.lineSeparator()
                + formatCauseChain(chain)
                + formatMatchScore(score)
                + formatMatchReasons(reasons)
                + "FIX:"
                + System.lineSeparator()
                + NO_AUTOMATIC_FIX
                + System.lineSeparator();

        return new DiagnosisResult(
                "DIAGNOSED",
                type,
                category.name(),
                "HIGH",
                "HIGH",
                location,
                summary,
                rootCause,
                evidence,
                FixType.NO_AUTOMATIC_FIX.name(),
                NO_AUTOMATIC_FIX,
                true,
                false,
                line,
                diagnosis,
                chain,
                reasons,
                score.value(),
                score.band(),
                score.factors(),
                RemediationMetadata.from(category, Set.of(FixType.NO_AUTOMATIC_FIX)));
    }

    private String safelyExplainKnownIncident(Incident incident) {
        try {
            return normalizeLlmResponse(llm.explainKnownIncident(incident));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String safelyAnalyzeUnknownLog(String context, IncidentCategory category) {
        try {
            return normalizeLlmResponse(llm.analyzeUnknownLog(context, category));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizeLlmResponse(String response) {
        return response == null || response.isBlank() ? null : response.trim();
    }

    private static String formatLlmSection(String analysis) {
        return analysis == null
                ? ""
                : System.lineSeparator()
                + "LLM ANALYSIS:"
                + System.lineSeparator()
                + analysis
                + System.lineSeparator();
    }

    private static String formatCauseChain(List<CauseChainAnalyzer.Cause> causes) {
        if (causes == null || causes.isEmpty()) {
            return "";
        }
        String lines = causes.stream()
                .map(cause -> "- line "
                        + cause.lineNumber()
                        + ": "
                        + cause.exceptionType()
                        + (cause.message().isBlank() ? "" : ": " + cause.message()))
                .collect(Collectors.joining(System.lineSeparator()));
        return "CAUSE CHAIN:"
                + System.lineSeparator()
                + lines
                + System.lineSeparator()
                + System.lineSeparator();
    }

    private static String formatMatchScore(MatchConfidenceScorer.Score score) {
        if (score == null) {
            return "";
        }
        String factors = score.factors().stream()
                .map(factor -> "- " + factor)
                .collect(Collectors.joining(System.lineSeparator()));
        return "MATCH SCORE: "
                + score.value()
                + "/100 ("
                + score.band()
                + ")"
                + System.lineSeparator()
                + (factors.isBlank() ? "" : factors + System.lineSeparator())
                + System.lineSeparator();
    }

    private static String formatMatchReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "";
        }
        return "WHY MATCHED:"
                + System.lineSeparator()
                + reasons.stream()
                .map(reason -> "- " + reason)
                .collect(Collectors.joining(System.lineSeparator()))
                + System.lineSeparator()
                + System.lineSeparator();
    }

    private static String formatFixTypes(Set<FixType> allowed) {
        return allowed.isEmpty()
                ? "NONE"
                : allowed.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static boolean isConcurrencyFailure(String lower) {
        return lower.contains("optimisticlock")
                || lower.contains("staleobjectstate")
                || lower.contains("deadlock")
                || lower.contains("could not serialize access");
    }

    private static boolean isBusinessInvariantFailure(String lower) {
        return lower.contains("illegalstateexception")
                && (lower.contains("transition")
                || lower.contains("state")
                || lower.contains("not allowed"));
    }

    private static IncidentCategory inferUnknownCategory(String lower) {
        return lower.contains("resttemplate") || lower.contains("sockettimeoutexception")
                ? IncidentCategory.INFRASTRUCTURE
                : IncidentCategory.UNKNOWN;
    }

    public record DiagnosisResult(
            String status,
            String type,
            String category,
            String severity,
            String confidence,
            String location,
            String summary,
            String rootCause,
            String evidence,
            String fixType,
            String fix,
            boolean humanReviewRequired,
            boolean llmUsed,
            Integer failureLine,
            String diagnosis,
            List<CauseChainAnalyzer.Cause> causeChain,
            List<String> matchReasons,
            int matchScore,
            String matchConfidence,
            List<String> matchScoreFactors,
            RemediationMetadata remediation
    ) {
        static DiagnosisResult empty(String message) {
            return new DiagnosisResult(
                    "NO_FAILURE",
                    "NONE",
                    "NONE",
                    "NONE",
                    "NONE",
                    "—",
                    message,
                    "—",
                    "—",
                    "NONE",
                    "No remediation required.",
                    false,
                    false,
                    null,
                    message + System.lineSeparator(),
                    List.of(),
                    List.of(),
                    0,
                    "NONE",
                    List.of(),
                    null);
        }
    }
}
