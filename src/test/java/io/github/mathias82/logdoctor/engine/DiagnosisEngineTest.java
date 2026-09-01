package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisEngineTest {

    private final DiagnosisEngine engine = new DiagnosisEngine(new StubLlmClient());

    @Test
    void returnsNoFailureForBlankInput() {
        var result = engine.analyzeStructured("   ");

        assertThat(result.status()).isEqualTo("NO_FAILURE");
        assertThat(result.llmUsed()).isFalse();
        assertThat(result.humanReviewRequired()).isFalse();
    }

    @Test
    void returnsNoFailureWhenNoExceptionIsPresent() {
        var result = engine.analyzeStructured("2026-09-01 INFO application started successfully");

        assertThat(result.status()).isEqualTo("NO_FAILURE");
        assertThat(result.summary()).contains("No obvious failure");
    }

    @Test
    void usesLlmForUnknownFailure() {
        var result = engine.analyzeStructured("java.lang.RuntimeException: boom");

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.type()).isEqualTo("UNKNOWN_FAILURE");
        assertThat(result.llmUsed()).isTrue();
        assertThat(result.fixType()).isEqualTo("NO_AUTOMATIC_FIX");
        assertThat(result.diagnosis()).contains("stub unknown analysis");
    }

    @Test
    void fallsBackToHumanReviewWhenLlmIsUnavailable() {
        var failingEngine = new DiagnosisEngine(new FailingLlmClient());

        var result = failingEngine.analyzeStructured("java.lang.RuntimeException: boom");

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.llmUsed()).isFalse();
        assertThat(result.humanReviewRequired()).isTrue();
        assertThat(result.fixType()).isEqualTo("NO_AUTOMATIC_FIX");
        assertThat(result.fix()).contains("LLM analysis is unavailable");
        assertThat(result.diagnosis()).doesNotContain("LLM ANALYSIS:");
    }

    @Test
    void treatsBlankLlmResponseAsUnavailable() {
        var blankEngine = new DiagnosisEngine(new BlankLlmClient());

        var result = blankEngine.analyzeStructured("java.lang.RuntimeException: boom");

        assertThat(result.llmUsed()).isFalse();
        assertThat(result.humanReviewRequired()).isTrue();
    }

    @Test
    void marksConcurrencyFailureForHumanReviewWithoutCallingLlm() {
        var result = engine.analyzeStructured("org.hibernate.OptimisticLockException: row was updated by another transaction");

        assertThat(result.type()).isEqualTo("CONCURRENCY_FAILURE");
        assertThat(result.humanReviewRequired()).isTrue();
        assertThat(result.llmUsed()).isFalse();
        assertThat(result.fixType()).isEqualTo("NO_AUTOMATIC_FIX");
        assertThat(result.location()).contains("OptimisticLockException");
    }

    @Test
    void infersInfrastructureCategoryCaseInsensitively() {
        var result = engine.analyzeStructured("java.net.SocketTimeoutException: timeout from RestTemplate");

        assertThat(result.category()).isEqualTo("INFRASTRUCTURE");
    }

    private static final class StubLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            return "stub known analysis";
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            return "stub unknown analysis";
        }
    }

    private static final class FailingLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            throw new IllegalStateException("Ollama unavailable");
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            throw new IllegalStateException("Ollama unavailable");
        }
    }

    private static final class BlankLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            return "  ";
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            return "  ";
        }
    }
}
