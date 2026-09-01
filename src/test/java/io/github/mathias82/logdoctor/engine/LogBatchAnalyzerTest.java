package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogBatchAnalyzerTest {

    private final LogBatchAnalyzer analyzer = new LogBatchAnalyzer(new DiagnosisEngine(new StubLlmClient()));

    @Test
    void groupsRepeatedFailuresByFingerprint() {
        String log = """
                ERROR request failed
                java.lang.RuntimeException: order 123 failed
                at com.acme.OrderService.run(OrderService.java:41)
                ERROR request failed
                java.lang.RuntimeException: order 456 failed
                at com.acme.OrderService.run(OrderService.java:41)
                """;

        var result = analyzer.analyze(log);

        assertThat(result.failureBlocks()).isGreaterThan(0);
        assertThat(result.uniqueIncidents()).isGreaterThan(0);
        assertThat(result.incidents()).isNotEmpty();
    }

    @Test
    void returnsEmptyBatchForBlankInput() {
        var result = analyzer.analyze("   ");
        assertThat(result.failureBlocks()).isZero();
        assertThat(result.uniqueIncidents()).isZero();
        assertThat(result.incidents()).isEmpty();
    }

    private static final class StubLlmClient implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return "known"; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return "unknown"; }
    }
}
