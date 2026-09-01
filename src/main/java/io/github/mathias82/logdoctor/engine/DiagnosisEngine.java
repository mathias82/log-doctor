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

    /**
     * Backward-compatible CLI entry point.
     */
    public void analyze(String log) {
        System.out.print(analyzeToText(log));
    }

    /**
     * Runs the complete diagnosis pipeline and returns a renderable text result.
     * Keeping diagnosis output as a return value allows the same engine to be
     * reused by the CLI and the embedded web UI without redirecting stdout.
     */
    public String analyzeToText(String log) {
        StringBuilder output = new StringBuilder();

        if (log == null || log.isBlank()) {
            return "No log content provided.\n";
        }

        LogParser parser = new LogParser();
        FailureLocator locator = new FailureLocator();
        FailureContextExtractor contextExtractor = new FailureContextExtractor();

        var lines = parser.parse(log);
        var failureOpt = locator.locate(lines);

        if (failureOpt.isEmpty()) {
            return "No obvious failure found.\n";
        }

        var failure = failureOpt.get();
        String contextText = contextExtractor.extract(lines, failure, 8);

        RuleContext ruleContext = new RuleContext(
                lines,
                failure,
                contextText
        );

        var incidentOpt = detector.detect(ruleContext)
                .filter(i -> i.confidence() == Confidence.HIGH);

        if (incidentOpt.isPresent()) {
            var incident = incidentOpt.get();
            incident.setEvidence(contextText);
            incident.setComponent(
                    failure.blameLocation() != null
                            ? failure.blameLocation().content()
                            : failure.rootCause().content()
            );

            output.append(incident.format()).append(System.lineSeparator());

            var allowedFixes = FixPolicy.allowedFixes(incident.category());
            if (allowedFixes.equals(Set.of(FixType.NO_AUTOMATIC_FIX))) {
                output.append("FIX:").append(System.lineSeparator())
                        .append("No safe automatic fix, human investigation required.")
                        .append(System.lineSeparator());
                return output.toString();
            }

            output.append("LLM ANALYSIS:").append(System.lineSeparator());
            output.append(llm.explainKnownIncident(incident)).append(System.lineSeparator());
            return output.toString();
        }

        output.append("Unknown failure detected at line ")
                .append(failure.rootCause().lineNumber())
                .append(System.lineSeparator())
                .append(contextText)
                .append(System.lineSeparator());

        String concurrencyText = contextText.toLowerCase();

        if (concurrencyText.contains("optimisticlock")
                || concurrencyText.contains("staleobjectstate")
                || concurrencyText.contains("deadlock")
                || concurrencyText.contains("could not serialize access")) {

            output.append("WHERE:").append(System.lineSeparator())
                    .append("Concurrency / data consistency failure detected in application layer")
                    .append(System.lineSeparator()).append(System.lineSeparator())
                    .append("FIX:").append(System.lineSeparator())
                    .append("No safe automatic fix, human investigation required.")
                    .append(System.lineSeparator());
            return output.toString();
        }

        if (concurrencyText.contains("illegalstateexception")
                && (concurrencyText.contains("transition")
                || concurrencyText.contains("state")
                || concurrencyText.contains("not allowed"))) {

            output.append("WHERE:").append(System.lineSeparator())
                    .append("Domain state machine / business invariant violation")
                    .append(System.lineSeparator()).append(System.lineSeparator())
                    .append("FIX:").append(System.lineSeparator())
                    .append("No safe automatic fix, human investigation required.")
                    .append(System.lineSeparator());
            return output.toString();
        }

        IncidentCategory inferredCategory =
                contextText.contains("RestTemplate")
                        || contextText.contains("SocketTimeoutException")
                        ? IncidentCategory.INFRASTRUCTURE
                        : IncidentCategory.UNKNOWN;

        output.append("LLM ANALYSIS:").append(System.lineSeparator());
        output.append(llm.analyzeUnknownLog(contextText, inferredCategory)).append(System.lineSeparator());
        return output.toString();
    }
}
