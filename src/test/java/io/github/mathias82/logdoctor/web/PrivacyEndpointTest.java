package io.github.mathias82.logdoctor.web;

import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.llm.LlmClient;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyEndpointTest {
    private HttpServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine(new NoopLlmClient()));
        client = HttpClient.newHttpClient();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void privacyEndpointReportsRuntimeBoundary() throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/privacy")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"analysisProcess\":\"LOCAL\"")
                .contains("\"redactionBeforeLlm\":true")
                .contains("\"automaticRemediation\":false")
                .contains("\"llmEndpointScope\"");
    }

    @Test
    void servesPrivacyDashboardClient() throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/privacy.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/javascript; charset=utf-8");
        assertThat(response.body()).contains("/api/privacy");
    }

    private static final class NoopLlmClient implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return null; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return null; }
    }
}
