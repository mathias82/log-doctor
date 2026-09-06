package io.github.mathias82.logdoctor.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionReportEndpointTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void batchAnalysisReturnsCountsAndNeverSensitiveValuesInTheReport() throws Exception {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine(new NoopLlmClient()));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String log = """
                ERROR request failed password=hunter2 user=john.doe@example.com remote=10.20.30.40
                java.lang.RuntimeException: boom
                """;
        String body = JSON.writeValueAsString(java.util.Map.of("log", log));

        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze/batch"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode report = JSON.readTree(response.body()).path("redactionReport");
        assertThat(report.path("totalRedactions").asInt()).isEqualTo(3);
        assertThat(report.path("sensitiveDataDetected").asBoolean()).isTrue();
        assertThat(report.path("appliedBeforeLlm").asBoolean()).isTrue();
        assertThat(report.path("valuesExposed").asBoolean()).isFalse();
        assertThat(report.path("categories").path("SECRET_ASSIGNMENT").asInt()).isEqualTo(1);
        assertThat(report.path("categories").path("EMAIL").asInt()).isEqualTo(1);
        assertThat(report.path("categories").path("IPV4").asInt()).isEqualTo(1);
        assertThat(report.toString()).doesNotContain("hunter2", "john.doe@example.com", "10.20.30.40");
    }

    @Test
    void cleanInputReturnsZeroRedactions() throws Exception {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine(new NoopLlmClient()));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String body = JSON.writeValueAsString(java.util.Map.of("log", "INFO service started successfully"));

        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode report = JSON.readTree(response.body()).path("redactionReport");
        assertThat(report.path("totalRedactions").asInt()).isZero();
        assertThat(report.path("sensitiveDataDetected").asBoolean()).isFalse();
        assertThat(report.path("categories").isEmpty()).isTrue();
    }

    private static final class NoopLlmClient implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return null; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return null; }
    }
}
