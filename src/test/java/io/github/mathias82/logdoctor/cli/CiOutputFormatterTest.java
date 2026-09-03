package io.github.mathias82.logdoctor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CiOutputFormatterTest {
    private static final ObjectMapper JSON = new ObjectMapper();

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
    void emitsSarif210FindingWithStableRuleAndLocation() throws Exception {
        var result = engine().analyzeStructured("java.lang.NullPointerException: order was null");

        JsonNode sarif = JSON.readTree(CiOutputFormatter.sarif(result, Path.of("logs", "app.log")));

        assertThat(sarif.path("version").asText()).isEqualTo("2.1.0");
        JsonNode run = sarif.path("runs").get(0);
        assertThat(run.path("tool").path("driver").path("name").asText()).isEqualTo("Log Doctor");
        assertThat(run.path("tool").path("driver").path("rules").get(0).path("id").asText()).startsWith("LOGDOCTOR-");
        JsonNode finding = run.path("results").get(0);
        assertThat(finding.path("ruleId").asText()).startsWith("LOGDOCTOR-");
        assertThat(finding.path("locations").get(0).path("physicalLocation").path("artifactLocation").path("uri").asText())
                .isEqualTo("logs/app.log");
    }

    @Test
    void emitsEmptySarifResultsForHealthyLog() throws Exception {
        var result = engine().analyzeStructured("INFO service started successfully");
        JsonNode sarif = JSON.readTree(CiOutputFormatter.sarif(result, Path.of("logs", "app.log")));
        assertThat(sarif.path("runs").get(0).path("results").isEmpty()).isTrue();
    }

    @Test
    void parsesSupportedCliFormats() {
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log"})).isEqualTo("text");
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log", "--format=json"})).isEqualTo("json");
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log", "--format", "github"})).isEqualTo("github");
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log", "--format", "sarif"})).isEqualTo("sarif");
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
        public String explainKnownIncident(Incident incident) { return null; }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return null; }
    }
}
