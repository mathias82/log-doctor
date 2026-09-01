package io.github.mathias82.logdoctor.web;

import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LogDoctorWebServerTest {

    private HttpServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine(new StubLlmClient()));
        client = HttpClient.newHttpClient();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void healthEndpointReturnsUpAndSecurityHeaders() throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
    }

    @Test
    void healthRejectsUnsupportedMethodAndReturnsAllowHeader() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/health"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).contains("GET");
    }

    @Test
    void analyzeReturnsStructuredJson() throws Exception {
        var response = analyze("INFO application started");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"NO_FAILURE\"");
        assertThat(response.body()).contains("\"llmUsed\":false");
    }

    @Test
    void analyzeAcceptsLogAtFiveMegabyteBoundaryDespiteJsonEnvelope() throws Exception {
        String log = "x".repeat(LogDoctorWebServer.MAX_LOG_BYTES);

        var response = analyze(log);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"NO_FAILURE\"");
    }

    @Test
    void analyzeRejectsLogOverFiveMegabytes() throws Exception {
        String log = "x".repeat(LogDoctorWebServer.MAX_LOG_BYTES + 1);

        var response = analyze(log);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("5 MB limit");
    }

    @Test
    void analyzeRejectsUnsupportedContentType() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("INFO"))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).contains("Content-Type must be application/json");
    }

    @Test
    void analyzeRejectsMalformedJsonWithoutLeakingExceptionDetails() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not-json}"))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid JSON request");
        assertThat(response.body()).doesNotContain("JsonParseException");
    }

    private HttpResponse<String> analyze(String log) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"log\":\"" + log + "\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
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
