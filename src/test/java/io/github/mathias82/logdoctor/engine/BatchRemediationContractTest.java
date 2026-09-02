package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchRemediationContractTest {

    private final LogBatchAnalyzer analyzer = new LogBatchAnalyzer(new DiagnosisEngine(new StubLlmClient()));

    @Test
    void carriesBackendRemediationMetadataIntoGroupedIncident() {
        String log = failureLog("java.lang.NoClassDefFoundError: com/acme/Missing");

        var result = analyzer.analyze(log);
        assertThat(result.incidents()).hasSize(1);
        var incident = result.incidents().getFirst();

        assertThat(incident.remediation()).isNotNull();
        assertThat(incident.remediation().safety()).isEqualTo("REVIEW_BEFORE_APPLY");
        assertThat(incident.remediation().allowedActions()).containsExactly("SPRING_CONFIG");
        assertThat(incident.remediation().verificationSteps()).isNotEmpty();
        assertThat(incident.remediation().automaticExecutionAllowed()).isFalse();
    }

    @Test
    void includesRemediationSafetyInMarkdownReport() {
        String log = failureLog("java.lang.NoClassDefFoundError: com/acme/Missing");

        String report = analyzer.analyze(log).reportMarkdown();

        assertThat(report)
                .contains("Remediation safety: REVIEW_BEFORE_APPLY")
                .contains("Automatic execution allowed: false")
                .contains("Allowed action types: SPRING_CONFIG")
                .contains("Verification steps:");
    }

    @Test
    void keepsUnknownGroupedIncidentHumanReviewOnlyAfterLlmEnrichment() {
        String log = failureLog("java.lang.RuntimeException: unusual failure");

        var result = analyzer.analyze(log);
        assertThat(result.incidents()).hasSize(1);
        var incident = result.incidents().getFirst();

        assertThat(incident.remediation().safety()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(incident.remediation().allowedActions()).containsExactly("NO_AUTOMATIC_FIX");
        assertThat(incident.remediation().automaticExecutionAllowed()).isFalse();
    }

    private static String failureLog(String exception) {
        return String.join(System.lineSeparator(),
                "2026-09-02 18:00:00 ERROR request failed",
                exception,
                "    at com.acme.OrderService.run(OrderService.java:41)");
    }

    private static final class StubLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            return "known";
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            return "unknown";
        }
    }
}
