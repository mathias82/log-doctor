package io.github.mathias82.logdoctor.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Describes the runtime privacy boundary without exposing configured endpoint details.
 */
final class PrivacyStatus {
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";

    private PrivacyStatus() {}

    static Map<String, Object> current() {
        String configuredUrl = setting("log.doctor.ollama.url", "LOG_DOCTOR_OLLAMA_URL", DEFAULT_OLLAMA_URL);
        boolean localEndpoint = isLoopback(configuredUrl);
        return Map.of(
                "analysisProcess", "LOCAL",
                "deterministicAnalysis", "LOCAL",
                "llmProvider", "OLLAMA",
                "llmEndpointScope", localEndpoint ? "LOCAL_MACHINE" : "REMOTE_CONFIGURED",
                "logsLeaveMachineForConfiguredLlm", !localEndpoint,
                "redactionBeforeLlm", true,
                "rawLogsInMetrics", false,
                "automaticRemediation", false,
                "summary", localEndpoint
                        ? "Deterministic analysis and configured Ollama run on this machine. Sensitive data is redacted before the LLM boundary."
                        : "Deterministic analysis runs locally, but the configured Ollama endpoint is remote. Sensitive data is redacted before the LLM boundary."
        );
    }

    static boolean isLoopback(String rawUrl) {
        try {
            String host = URI.create(rawUrl).getHost();
            if (host == null) return false;
            String normalized = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(normalized)
                    || "127.0.0.1".equals(normalized)
                    || "::1".equals(normalized)
                    || "0:0:0:0:0:0:0:1".equals(normalized);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String setting(String systemProperty, String environmentVariable, String fallback) {
        String propertyValue = System.getProperty(systemProperty);
        if (propertyValue != null && !propertyValue.isBlank()) return propertyValue.trim();
        String environmentValue = System.getenv(environmentVariable);
        if (environmentValue != null && !environmentValue.isBlank()) return environmentValue.trim();
        return fallback;
    }
}
