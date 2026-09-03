package io.github.mathias82.logdoctor.web;

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

class PrometheusMetricsEndpointTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exposesPrometheusScrapeFormatWithApiVersionHeader() throws Exception {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine(new NoopLlmClient()));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .contains("text/plain; version=0.0.4; charset=utf-8");
        assertThat(response.headers().firstValue(LogDoctorWebServer.API_VERSION_HEADER))
                .contains(LogDoctorWebServer.API_VERSION);
        assertThat(response.body())
                .contains("# TYPE log_doctor_analyses_total counter")
                .contains("log_doctor_analyses_total 0");
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
