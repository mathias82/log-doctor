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
        assertThat(result.diagnosis()).contains("stub unknown analysis");
    }

    @Test
    void marksConcurrencyFailureForHumanReviewWithoutCallingLlm() {
        var result = engine.analyzeStructured("org.hibernate.OptimisticLockException: row was updated by another transaction");

        assertThat(result.type()).isEqualTo("CONCURRENCY_FAILURE");
        assertThat(result.humanReviewRequired()).isTrue();
        assertThat(result.llmUsed()).isFalse();
        assertThat(result.fixType()).isEqualTo("NO_AUTOMATIC_FIX");
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
}
