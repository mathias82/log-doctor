package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.FixPolicy;
import io.github.mathias82.logdoctor.core.FixType;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import io.github.mathias82.logdoctor.llm.OllamaLlmClient;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DiagnosisEngine {

    private static final int CONTEXT_RADIUS = 8;
    private static final String NO_AUTOMATIC_FIX = "No safe automatic fix, human investigation required.";

    private final IncidentDetector detector;
    private final LogParser parser;
    private final FailureLocator failureLocator;
    private final FailureContextExtractor contextExtractor;
    private final CauseChainAnalyzer causeChainAnalyzer;
    private final LlmClient llm;

    public DiagnosisEngine() {
        this(new OllamaLlmClient());
    }

    public DiagnosisEngine(LlmClient llm) {
        this(new IncidentDetector(), new LogParser(), new FailureLocator(), new FailureContextExtractor(), new CauseChainAnalyzer(), llm);
    }

    DiagnosisEngine(
            IncidentDetector detector,
            LogParser parser,
            FailureLocator failureLocator,
            FailureContextExtractor contextExtractor,
            LlmClient llm
    ) {
        this(detector, parser, failureLocator, contextExtractor, new CauseChainAnalyzer(), llm);
    }

    DiagnosisEngine(
            IncidentDetector detector,
            LogParser parser,
            FailureLocator failureLocator,
            FailureContextExtractor contextExtractor,
            CauseChainAnalyzer causeChainAnalyzer,
            LlmClient llm
    ) {
        this.detector = detector;
        this.parser = parser;
        this.failureLocator = failureLocator;
        this.contextExtractor = contextExtractor;
        this.causeChainAnalyzer = causeChainAnalyzer;
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
        List<CauseChainAnalyzer.Cause> causeChain = causeChainAnalyzer.analyze(lines);

        var detectionOpt = detector.detectDetailed(new RuleContext(lines, failure, contextText))
                .filter(detection -> detection.incident().confidence() == Confidence.HIGH);

        if (detectionOpt.isPresent()) {
            var detection = detectionOpt.get();
            return diagnosedIncident(
                    detection.incident(),
                    contextText,
                    location,
                    failure.rootCause().lineNumber(),
                    allowLlm,
                    causeChain,
                    detection.reasons()
            );
        }

        String lower = contextText.toLowerCase(Locale.ROOT);
        if (isConcurrencyFailure(lower)) {
            return manualReview(
                    failure.rootCause().lineNumber(),
                    location,
                    "CONCURRENCY_FAILURE",
                    "APPLICATION",
                    "Concurrency / data consistency failure",
                    "Concurrency / data consistency failure detected in application layer",
                    contextText,
                    causeChain,
                    List.of("Matched protected concurrency fallback", "Concurrency signature found in failure context")
            );
        }

        if (isBusinessInvariantFailure(lower)) {
            return manualReview(
                    failure.rootCause().lineNumber(),
                    location,
                    "BUSINESS_INVARIANT",
                    "APPLICATION",
                    "Domain state machine violation",
                    "Domain state machine / business invariant violation",
                    contextText,
                    causeChain,
                    List.of("Matched protected business-invariant fallback", "IllegalStateException state/transition signature found")
            );
        }

        String deepestCause = causeChain.isEmpty()
                ? failure.rootCause().content()
                : causeChain.get(causeChain.size() - 1).evidence();
        return unknownFailure(
                contextText,
                lower,
                location,
                deepestCause,
                failure.rootCause().lineNumber(),
                allowLlm,
                causeChain
        );
    }

    private DiagnosisResult diagnosedIncident(
            Incident incident,
            String evidence,
            String location,
            int failureLine,
            boolean allowLlm,
            List<CauseChainAnalyzer.Cause> causeChain,
            List<String> matchReasons
    ) {
        incident.setEvidence(evidence);
        incident.setComponent(location);

        Set<FixType> allowedFixes = FixPolicy.allowedFixes(incident.category());
        boolean humanReview = allowedFixes.contains(FixType.NO_AUTOMATIC_FIX);
        String fixType = humanReview ? FixType.NO_AUTOMATIC_FIX.name() : formatFixTypes(allowedFixes);
        String fix = humanReview ? NO_AUTOMATIC_FIX : incident.recommendation();
        String llmAnalysis = !allowLlm || humanReview ? null : safelyExplainKnownIncident(incident);

        String diagnosis = incident.format() + System.lineSeparator()
                + formatCauseChain(causeChain)
                + formatMatchReasons(matchReasons)
                + "FIX:" + System.lineSeparator() + fix + System.lineSeparator()
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
                humanReview,
                llmAnalysis != null,
                failureLine,
                diagnosis,
                causeChain,
                matchReasons
        );
    }

    private DiagnosisResult unknownFailure(
            String contextText,
            String lower,
            String location,
            String rootCause,
            int failureLine,
            boolean allowLlm,
            List<CauseChainAnalyzer.Cause> causeChain
    ) {
        IncidentCategory category = inferUnknownCategory(lower);
        String llmAnalysis = allowLlm ? safelyAnalyzeUnknownLog(contextText, category) : null;
        String fix = llmAnalysis == null
                ? "No deterministic rule matched and local LLM analysis is unavailable. Human review required."
                : "Review the local LLM analysis and supporting evidence.";
        List<String> matchReasons = List.of("No deterministic rule matched the failure context");

        String diagnosis = "Unknown failure detected at line " + failureLine + System.lineSeparator()
                + contextText + System.lineSeparator()
                + formatCauseChain(causeChain)
                + formatMatchReasons(matchReasons)
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
                contextText,
                FixType.NO_AUTOMATIC_FIX.name(),
                fix,
                true,
                llmAnalysis != null,
                failureLine,
                diagnosis,
                causeChain,
                matchReasons
        );
    }

    private DiagnosisResult manualReview(
            int line,
            String location,
            String type,
            String category,
            String summary,
            String rootCause,
            String evidence,
            List<CauseChainAnalyzer.Cause> causeChain,
            List<String> matchReasons
    ) {
        String diagnosis = "WHERE:" + System.lineSeparator() + location + System.lineSeparator() + System.lineSeparator()
                + "ROOT CAUSE:" + System.lineSeparator() + rootCause + System.lineSeparator() + System.lineSeparator()
                + formatCauseChain(causeChain)
                + formatMatchReasons(matchReasons)
                + "FIX:" + System.lineSeparator() + NO_AUTOMATIC_FIX + System.lineSeparator();

        return new DiagnosisResult(
                "DIAGNOSED",
                type,
                category,
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
                causeChain,
                matchReasons
        );
    }

    private String safelyExplainKnownIncident(Incident incident) {
        try {
            return normalizeLlmResponse(llm.explainKnownIncident(incident));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String safelyAnalyzeUnknownLog(String contextText, IncidentCategory category) {
        try {
            return normalizeLlmResponse(llm.analyzeUnknownLog(contextText, category));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizeLlmResponse(String response) {
        return response == null || response.isBlank() ? null : response.trim();
    }

    private static String formatLlmSection(String llmAnalysis) {
        if (llmAnalysis == null) {
            return "";
        }
        return System.lineSeparator() + "LLM ANALYSIS:" + System.lineSeparator()
                + llmAnalysis + System.lineSeparator();
    }

    private static String formatCauseChain(List<CauseChainAnalyzer.Cause> causeChain) {
        if (causeChain == null || causeChain.isEmpty()) {
            return "";
        }
        String chain = causeChain.stream()
                .map(cause -> "- line " + cause.lineNumber() + ": " + cause.exceptionType()
                        + (cause.message().isBlank() ? "" : ": " + cause.message()))
                .collect(Collectors.joining(System.lineSeparator()));
        return "CAUSE CHAIN:" + System.lineSeparator() + chain + System.lineSeparator() + System.lineSeparator();
    }

    private static String formatMatchReasons(List<String> matchReasons) {
        if (matchReasons == null || matchReasons.isEmpty()) {
            return "";
        }
        return "WHY MATCHED:" + System.lineSeparator()
                + matchReasons.stream().map(reason -> "- " + reason).collect(Collectors.joining(System.lineSeparator()))
                + System.lineSeparator() + System.lineSeparator();
    }

    private static String formatFixTypes(Set<FixType> allowedFixes) {
        if (allowedFixes.isEmpty()) {
            return "NONE";
        }
        return allowedFixes.stream()
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
                && (lower.contains("transition") || lower.contains("state") || lower.contains("not allowed"));
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
            List<String> matchReasons
    ) {
        static DiagnosisResult empty(String message) {
            return new DiagnosisResult(
                    "NO_FAILURE", "NONE", "NONE", "NONE", "NONE", "—", message,
                    "—", "—", "NONE", "No remediation required.", false, false, null,
                    message + System.lineSeparator(), List.of(), List.of()
            );
        }
    }
}
