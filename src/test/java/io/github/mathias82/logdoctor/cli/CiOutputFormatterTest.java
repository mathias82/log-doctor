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
