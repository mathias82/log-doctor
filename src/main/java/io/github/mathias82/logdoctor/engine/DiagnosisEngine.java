package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.FixPolicy;
import io.github.mathias82.logdoctor.core.FixType;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import io.github.mathias82.logdoctor.llm.OllamaLlmClient;

import java.util.Set;

public class DiagnosisEngine {

    private final IncidentDetector detector = new IncidentDetector();
    private final LlmClient llm;

    public DiagnosisEngine() {
        this(new OllamaLlmClient());
    }

    public DiagnosisEngine(LlmClient llm) {
        this.llm = llm;
    }

    public void analyze(String log) {
        System.out.print(analyzeToText(log));
    }

    public String analyzeToText(String log) {
        return analyzeStructured(log).diagnosis();
    }

    public DiagnosisResult analyzeStructured(String log) {
        if (log == null || log.isBlank()) {
            return DiagnosisResult.empty("No log content provided.");
        }

        var lines = new LogParser().parse(log);
        var failureOpt = new FailureLocator().locate(lines);
        if (failureOpt.isEmpty()) {
            return DiagnosisResult.empty("No obvious failure found.");
        }

        var failure = failureOpt.get();
        String contextText = new FailureContextExtractor().extract(lines, failure, 8);
        var incidentOpt = detector.detect(new RuleContext(lines, failure, contextText))
                .filter(i -> i.confidence() == Confidence.HIGH);

        if (incidentOpt.isPresent()) {
            var incident = incidentOpt.get();
            incident.setEvidence(contextText);
            incident.setComponent(failure.blameLocation() != null
                    ? failure.blameLocation().content()
                    : failure.rootCause().content());

            var allowedFixes = FixPolicy.allowedFixes(incident.category());
            boolean humanReview = allowedFixes.equals(Set.of(FixType.NO_AUTOMATIC_FIX));
            String fixType = humanReview ? FixType.NO_AUTOMATIC_FIX.name()
                    : allowedFixes.stream().map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("NONE");
            String llmAnalysis = humanReview ? null : llm.explainKnownIncident(incident);
            String fix = humanReview
                    ? "No safe automatic fix, human investigation required."
                    : incident.recommendation();

            String diagnosis = incident.format() + System.lineSeparator()
                    + "FIX:" + System.lineSeparator() + fix + System.lineSeparator()
                    + (llmAnalysis == null ? "" : System.lineSeparator() + "LLM ANALYSIS:" + System.lineSeparator() + llmAnalysis + System.lineSeparator());

            return new DiagnosisResult(
                    "DIAGNOSED", incident.type(), incident.category().name(), incident.severity().name(),
                    incident.confidence().name(), incident.component(), incident.summary(), incident.rootCause(),
                    incident.evidence(), fixType, fix, humanReview, llmAnalysis != null,
                    failure.rootCause().lineNumber(), diagnosis
            );
        }

        String lower = contextText.toLowerCase();
        if (lower.contains("optimisticlock") || lower.contains("staleobjectstate")
                || lower.contains("deadlock") || lower.contains("could not serialize access")) {
            return manualReview(failure.rootCause().lineNumber(), "CONCURRENCY_FAILURE", "APPLICATION",
                    "Concurrency / data consistency failure", "Concurrency / data consistency failure detected in application layer", contextText);
        }

        if (lower.contains("illegalstateexception")
                && (lower.contains("transition") || lower.contains("state") || lower.contains("not allowed"))) {
            return manualReview(failure.rootCause().lineNumber(), "BUSINESS_INVARIANT", "APPLICATION",
                    "Domain state machine violation", "Domain state machine / business invariant violation", contextText);
        }

        IncidentCategory category = contextText.contains("RestTemplate") || contextText.contains("SocketTimeoutException")
                ? IncidentCategory.INFRASTRUCTURE : IncidentCategory.UNKNOWN;
        String llmAnalysis = llm.analyzeUnknownLog(contextText, category);
        String diagnosis = "Unknown failure detected at line " + failure.rootCause().lineNumber() + System.lineSeparator()
                + contextText + System.lineSeparator() + "LLM ANALYSIS:" + System.lineSeparator() + llmAnalysis + System.lineSeparator();
        return new DiagnosisResult("UNKNOWN", "UNKNOWN_FAILURE", category.name(), "UNKNOWN", "LOW",
                failure.blameLocation() != null ? failure.blameLocation().content() : failure.rootCause().content(),
                "No deterministic rule matched this failure.", failure.rootCause().content(), contextText,
                "HUMAN_REVIEW", "Review the local LLM analysis and supporting evidence.", true, true,
                failure.rootCause().lineNumber(), diagnosis);
    }

    private DiagnosisResult manualReview(int line, String type, String category, String summary, String rootCause, String evidence) {
        String fix = "No safe automatic fix, human investigation required.";
        String diagnosis = "WHERE:" + System.lineSeparator() + rootCause + System.lineSeparator() + System.lineSeparator()
                + "FIX:" + System.lineSeparator() + fix + System.lineSeparator();
        return new DiagnosisResult("DIAGNOSED", type, category, "HIGH", "HIGH", rootCause, summary, rootCause,
                evidence, FixType.NO_AUTOMATIC_FIX.name(), fix, true, false, line, diagnosis);
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
            String diagnosis
    ) {
        static DiagnosisResult empty(String message) {
            return new DiagnosisResult("NO_FAILURE", "NONE", "NONE", "NONE", "NONE", "—", message,
                    "—", "—", "NONE", "No remediation required.", false, false, null, message + System.lineSeparator());
        }
    }
}
