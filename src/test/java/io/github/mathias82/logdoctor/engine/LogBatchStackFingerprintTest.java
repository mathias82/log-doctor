package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogBatchStackFingerprintTest {
    private final LogBatchAnalyzer analyzer = new LogBatchAnalyzer(new DiagnosisEngine(new StubLlmClient()));

    @Test
    void groupsSameCallPathAcrossDifferentLineNumbers() {
        String log = """
                2026-09-01 14:32:17 ERROR request failed
                java.lang.NullPointerException: returned null
                    at com.acme.OrderService.load(OrderService.java:41)
                    at com.acme.OrderController.get(OrderController.java:18)
                2026-09-01 14:32:30 ERROR request failed
                java.lang.NullPointerException: returned null
                    at com.acme.OrderService.load(OrderService.java:97)
                    at com.acme.OrderController.get(OrderController.java:33)
                """;

        var result = analyzer.analyze(log);

        assertThat(result.uniqueIncidents()).isEqualTo(1);
        assertThat(result.incidents().getFirst().count()).isEqualTo(2);
    }

    @Test
    void separatesSameDiagnosisFromDifferentCallPaths() {
        String log = """
                2026-09-01 14:32:17 ERROR request failed
                java.lang.NullPointerException: returned null
                    at com.acme.OrderService.load(OrderService.java:41)
                2026-09-01 14:32:30 ERROR request failed
                java.lang.NullPointerException: returned null
                    at com.acme.PaymentService.charge(PaymentService.java:41)
                """;

        var result = analyzer.analyze(log);

        assertThat(result.uniqueIncidents()).isEqualTo(2);
        assertThat(result.incidents()).allSatisfy(group -> assertThat(group.count()).isEqualTo(1));
    }

    private static final class StubLlmClient implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return "known"; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return "unknown"; }
    }
}
