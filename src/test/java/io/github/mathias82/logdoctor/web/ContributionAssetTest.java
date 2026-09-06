package io.github.mathias82.logdoctor.web;

import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ContributionAssetTest {
    private HttpServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void servesContributionHelperAndDashboardLoadsIt() throws Exception {
        server = LogDoctorWebServer.start(0, new DiagnosisEngine());
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient client = HttpClient.newHttpClient();

        var helper = client.send(HttpRequest.newBuilder(URI.create(base + "/contribute.js")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var page = client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(helper.statusCode()).isEqualTo(200);
        assertThat(helper.body()).contains("LogDoctorContribution", "issues/new", "Sanitized evidence");
        assertThat(page.body()).contains("contributionPanel", "/contribute.js", "Review sanitized GitHub issue");
    }
}
