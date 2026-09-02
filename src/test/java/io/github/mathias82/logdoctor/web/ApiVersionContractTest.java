package io.github.mathias82.logdoctor.web;

import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionContractTest {

    @Test
    void exposesVersionHeaderAndHealthPayload() throws Exception {
        HttpServer server = LogDoctorWebServer.start(0, new DiagnosisEngine());
        try {
            int port = server.getAddress().getPort();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/health"))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue(LogDoctorWebServer.API_VERSION_HEADER))
                    .contains(LogDoctorWebServer.API_VERSION);
            assertThat(response.body()).contains("\"apiVersion\":\"1\"");
        } finally {
            server.stop(0);
        }
    }
}
