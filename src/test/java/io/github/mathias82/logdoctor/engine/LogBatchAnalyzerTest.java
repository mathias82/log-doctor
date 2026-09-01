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
                2026-09-01 14:32:17 ERROR request failed
                java.lang.RuntimeException: order 123 failed
                at com.acme.OrderService.run(OrderService.java:41)
                2026-09-01 14:32:30 ERROR request failed
                java.lang.RuntimeException: order 456 failed
                at com.acme.OrderService.run(OrderService.java:41)
                """;

        var result = analyzer.analyze(log);

        assertThat(result.failureBlocks()).isGreaterThan(0);
        assertThat(result.uniqueIncidents()).isGreaterThan(0);
        assertThat(result.incidents()).isNotEmpty();
        assertThat(result.incidents().getFirst().firstSeen()).isNotNull();
        assertThat(result.incidents().getFirst().lastSeen()).isNotNull();
    }

    @Test
    void derivesCorrelationForConsecutiveDifferentFailures() {
        String log = """
                2026-09-01 14:32:17 ERROR first failure
                java.lang.RuntimeException: boom
                2026-09-01 14:32:40 ERROR second failure
                java.net.SocketTimeoutException: downstream timeout
                """;

        var result = analyzer.analyze(log);

        assertThat(result.failureBlocks()).isGreaterThanOrEqualTo(2);
        assertThat(result.correlations()).isNotEmpty();
        assertThat(result.correlations().getFirst().occurrences()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void returnsEmptyBatchForBlankInput() {
        var result = analyzer.analyze("   ");
        assertThat(result.failureBlocks()).isZero();
        assertThat(result.uniqueIncidents()).isZero();
        assertThat(result.incidents()).isEmpty();
        assertThat(result.correlations()).isEmpty();
    }

    private static final class StubLlmClient implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return "known"; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return "unknown"; }
    }
}
