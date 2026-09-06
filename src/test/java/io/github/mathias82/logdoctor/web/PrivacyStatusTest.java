package io.github.mathias82.logdoctor.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyStatusTest {

    @Test
    void recognizesLoopbackOllamaEndpoints() {
        assertThat(PrivacyStatus.isLoopback("http://localhost:11434")).isTrue();
        assertThat(PrivacyStatus.isLoopback("http://127.0.0.1:11434")).isTrue();
        assertThat(PrivacyStatus.isLoopback("http://[::1]:11434")).isTrue();
    }

    @Test
    void treatsNonLoopbackEndpointAsRemoteConfigured() {
        assertThat(PrivacyStatus.isLoopback("http://ollama.internal:11434")).isFalse();
        assertThat(PrivacyStatus.isLoopback("https://example.invalid")).isFalse();
    }

    @Test
    void defaultRuntimeStatusIsExplicitAboutSafetyBoundary() {
        var status = PrivacyStatus.current();

        assertThat(status.get("analysisProcess")).isEqualTo("LOCAL");
        assertThat(status.get("redactionBeforeLlm")).isEqualTo(true);
        assertThat(status.get("rawLogsInMetrics")).isEqualTo(false);
        assertThat(status.get("automaticRemediation")).isEqualTo(false);
    }
}
