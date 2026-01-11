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
    private final LlmClient llm = new OllamaLlmClient();

    public void analyze(String log) {

        LogParser parser = new LogParser();
        FailureLocator locator = new FailureLocator();
        FailureContextExtractor contextExtractor = new FailureContextExtractor();

        var lines = parser.parse(log);
        var failureOpt = locator.locate(lines);

        if (failureOpt.isEmpty()) {
            System.out.println("No obvious failure found.");
            return;
        }

        var failure = failureOpt.get();
        String contextText = contextExtractor.extract(lines, failure, 8);

        RuleContext ruleContext = new RuleContext(
                lines,
                failure,
                contextText
        );


        detector.detect(ruleContext)
                .filter(i -> i.confidence() == Confidence.HIGH)
                .ifPresentOrElse(
                        incident -> {
                            incident.setEvidence(contextText);

                            incident.setComponent(
                                    failure.blameLocation() != null
                                            ? failure.blameLocation().content()
                                            : failure.rootCause().content()
                            );

                            System.out.println(incident.format());

                            var allowedFixes = FixPolicy.allowedFixes(incident.category());
                            if (allowedFixes.size() == 1 && allowedFixes.contains(FixType.NO_AUTOMATIC_FIX)) {
                                System.out.println("""
                                FIX:
                                No safe automatic fix, human investigation required.
                                """);
                                return;
                            }

                            if (FixPolicy.allowedFixes(incident.category())
                                    .equals(Set.of(FixType.NO_AUTOMATIC_FIX))) {

                                System.out.println(incident.format());
                                System.out.println("No safe automatic fix, human investigation required.");
                                return;
                            }


                            System.out.println(llm.explainKnownIncident(incident));
                        },
                () -> {
                    System.out.println(
                            "Unknown failure detected at line "
                                    + failure.rootCause().lineNumber()
                    );
                    System.out.println(contextText);

                    String concurrencyText = contextText.toLowerCase();

                    if (concurrencyText.contains("optimisticlock")
                            || concurrencyText.contains("staleobjectstate")
                            || concurrencyText.contains("deadlock")
                            || concurrencyText.contains("could not serialize access")) {

                        System.out.println("""
                            WHERE:
                            Concurrency / data consistency failure detected in application layer
                            
                            FIX:
                            No safe automatic fix, human investigation required.
                            """);
                        return;
                    }

                    if (concurrencyText.contains("illegalstateexception")
                            && (concurrencyText.contains("transition")
                            || concurrencyText.contains("state")
                            || concurrencyText.contains("not allowed"))) {

                        System.out.println("""
                        WHERE:
                        Domain state machine / business invariant violation
                        
                        FIX:
                        No safe automatic fix, human investigation required.
                        """);
                        return;
                    }

                    IncidentCategory inferredCategory =
                            contextText.contains("RestTemplate")
                                    || contextText.contains("SocketTimeoutException")
                                    ? IncidentCategory.INFRASTRUCTURE
                                    : IncidentCategory.UNKNOWN;

                    System.out.println(llm.analyzeUnknownLog(contextText, inferredCategory));
                }
        );
    }
}
