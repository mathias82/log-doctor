package io.github.mathias82.logdoctor.cli;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CiOutputFormatterTest {

    @Test
    void formatsStructuredJsonForCiConsumers() {
        var result = engine().analyzeStructured("java.lang.NullPointerException: order was null");

        assertThat(CiOutputFormatter.json(result))
                .contains("\"status\" : \"DIAGNOSED\"")
                .contains("\"type\"")
                .contains("\"remediation\"");
    }

    @Test
    void formatsGitHubWorkflowAnnotation() {
        var result = engine().analyzeStructured("java.lang.NullPointerException: order was null");

        String annotation = CiOutputFormatter.github(result, Path.of("logs", "app.log"));

        assertThat(annotation)
                .startsWith("::warning file=logs/app.log")
                .contains("title=Log Doctor")
                .doesNotContain("\n");
    }

    @Test
    void parsesSupportedCliFormats() {
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log"})).isEqualTo("text");
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log", "--format=json"})).isEqualTo("json");
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log", "--format", "github"})).isEqualTo("github");
    }

    @Test
    void parsesCiFailurePolicies() {
        assertThat(AnalyzeCommand.resolveFailOn(new String[]{"--file", "app.log"})).isEqualTo("none");
        assertThat(AnalyzeCommand.resolveFailOn(new String[]{"--file", "app.log", "--fail-on=diagnosis"})).isEqualTo("diagnosis");
        assertThat(AnalyzeCommand.resolveFailOn(new String[]{"--file", "app.log", "--fail-on", "high"})).isEqualTo("high");
        assertThat(AnalyzeCommand.resolveFailOn(new String[]{"--file", "app.log", "--fail-on", "critical"})).isEqualTo("critical");
    }

    @Test
    void appliesSeverityAwareFailurePolicy() {
        var noFailure = engine().analyzeStructured("INFO service started successfully");
        var medium = engine().analyzeStructured("java.lang.NullPointerException: order was null");
        var high = engine().analyzeStructured("org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]");
        var critical = engine().analyzeStructured("java.lang.OutOfMemoryError: Java heap space");

        assertThat(AnalyzeCommand.shouldFail(noFailure, "diagnosis")).isFalse();
        assertThat(AnalyzeCommand.shouldFail(medium, "diagnosis")).isTrue();
        assertThat(AnalyzeCommand.shouldFail(medium, "high")).isFalse();
        assertThat(AnalyzeCommand.shouldFail(high, "high")).isTrue();
        assertThat(AnalyzeCommand.shouldFail(high, "critical")).isFalse();
        assertThat(AnalyzeCommand.shouldFail(critical, "critical")).isTrue();
        assertThat(AnalyzeCommand.shouldFail(critical, "none")).isFalse();
    }

    private static DiagnosisEngine engine() {
        return new DiagnosisEngine(new NoopLlmClient());
    }

    private static final class NoopLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            return null;
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            return null;
        }
    }
}
