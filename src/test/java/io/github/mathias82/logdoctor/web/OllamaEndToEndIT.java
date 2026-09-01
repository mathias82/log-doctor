package io.github.mathias82.logdoctor.web;

import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.llm.OllamaLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaEndToEndIT {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void unknownFailureIsEnrichedByRealOllamaThroughBatchHttpApi() throws Exception {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine(new OllamaLlmClient()));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String body = """
                {"log":"2026-09-01 14:32:17 ERROR checkout pipeline failed\\njava.lang.RuntimeException: quantum relay mismatch ZX-9001\\n    at com.acme.ExperimentalService.run(ExperimentalService.java:42)"}
                """;

        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze/batch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"type\":\"UNKNOWN_FAILURE\"");
        assertThat(response.body()).contains("\"uniqueIncidents\":1");
        assertThat(response.body()).contains("\"llmUsed\":true");
        assertThat(response.body()).contains("Review the local LLM analysis and supporting evidence.");
    }
}
