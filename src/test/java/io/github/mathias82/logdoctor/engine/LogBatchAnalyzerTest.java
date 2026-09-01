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
        assertThat(result.detectedFailureBlocks()).isEqualTo(2);
        assertThat(result.uniqueIncidents()).isGreaterThan(0);
        assertThat(result.incidents()).isNotEmpty();
        assertThat(result.incidents().getFirst().firstSeen()).isNotNull();
        assertThat(result.incidents().getFirst().lastSeen()).isNotNull();
        assertThat(result.truncated()).isFalse();
        assertThat(result.reportMarkdown()).contains("# Log Doctor Incident Report", "## Incident groups");
    }

    @Test
    void keepsCausedByStackTraceInsideParentFailureBlock() {
        String log = """
                2026-09-01 14:32:17 ERROR request failed
                java.lang.IllegalStateException: service failed
                    at com.acme.OrderService.run(OrderService.java:41)
                Caused by: java.net.SocketTimeoutException: downstream timeout
                    at com.acme.Client.call(Client.java:19)
                2026-09-01 14:32:18 INFO request completed
                """;

        var result = analyzer.analyze(log);

        assertThat(result.detectedFailureBlocks()).isEqualTo(1);
        assertThat(result.failureBlocks()).isEqualTo(1);
        assertThat(result.correlations()).isEmpty();
        assertThat(result.rootCauseChains()).isEmpty();
    }

    @Test
    void derivesScoredRootCauseCandidateForConsecutiveDifferentFailuresWithinWindow() {
        String log = """
                2026-09-01 14:32:17 ERROR first failure
                java.lang.RuntimeException: boom
                2026-09-01 14:32:40 ERROR second failure
                java.net.SocketTimeoutException: downstream timeout
                """;

        var result = analyzer.analyze(log);

        assertThat(result.failureBlocks()).isGreaterThanOrEqualTo(2);
        assertThat(result.correlations()).isNotEmpty();
        assertThat(result.rootCauseChains()).isNotEmpty();
        assertThat(result.rootCauseChains().getFirst().score()).isBetween(0, 100);
        assertThat(result.rootCauseChains().getFirst().reason()).contains("not proven causation");
    }

    @Test
    void detectsBurstAgainstPerMinuteBaseline() {
        String log = """
                2026-09-01 14:30:01 ERROR request failed
                java.lang.RuntimeException: boom 1
                2026-09-01 14:30:10 ERROR request failed
                java.lang.RuntimeException: boom 2
                2026-09-01 14:30:20 ERROR request failed
                java.lang.RuntimeException: boom 3
                2026-09-01 14:32:10 ERROR request failed
                java.lang.RuntimeException: boom 4
                """;

        var result = analyzer.analyze(log);

        assertThat(result.spikes()).isNotEmpty();
        assertThat(result.spikes().getFirst().count()).isEqualTo(3);
        assertThat(result.spikes().getFirst().multiplier()).isGreaterThanOrEqualTo(2.0);
        assertThat(result.reportMarkdown()).contains("## Spikes", "3 events near");
    }

    @Test
    void doesNotInventCorrelationWhenTimestampsAreMissing() {
        String log = """
                ERROR first failure
                java.lang.RuntimeException: boom
                ERROR second failure
                java.net.SocketTimeoutException: downstream timeout
                """;

        var result = analyzer.analyze(log);

        assertThat(result.detectedFailureBlocks()).isEqualTo(2);
        assertThat(result.correlations()).isEmpty();
        assertThat(result.rootCauseChains()).isEmpty();
    }

    @Test
    void doesNotCorrelateFailuresOutsideTimeWindow() {
        String log = """
                2026-09-01 14:30:00 ERROR first failure
                java.lang.RuntimeException: boom
                2026-09-01 14:35:00 ERROR second failure
                java.net.SocketTimeoutException: downstream timeout
                """;

        var result = analyzer.analyze(log);

        assertThat(result.correlations()).isEmpty();
        assertThat(result.rootCauseChains()).isEmpty();
    }

    @Test
    void comparesOffsetTimestampsByInstant() {
        String log = """
                2026-09-01T14:32:17+02:00 ERROR first failure
                java.lang.RuntimeException: boom
                2026-09-01T12:33:00Z ERROR second failure
                java.net.SocketTimeoutException: downstream timeout
                """;

        var result = analyzer.analyze(log);

        assertThat(result.correlations()).isNotEmpty();
        assertThat(result.rootCauseChains()).isNotEmpty();
    }

    @Test
    void returnsEmptyBatchForBlankInput() {
        var result = analyzer.analyze("   ");
        assertThat(result.failureBlocks()).isZero();
        assertThat(result.detectedFailureBlocks()).isZero();
        assertThat(result.uniqueIncidents()).isZero();
        assertThat(result.incidents()).isEmpty();
        assertThat(result.correlations()).isEmpty();
        assertThat(result.rootCauseChains()).isEmpty();
        assertThat(result.spikes()).isEmpty();
        assertThat(result.reportMarkdown()).contains("No log content was provided");
        assertThat(result.truncated()).isFalse();
    }

    private static final class StubLlmClient implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return "known"; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return "unknown"; }
    }
}
