package io.github.mathias82.logdoctor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class LogDoctorWebServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_LOG_BYTES = 5 * 1024 * 1024;

    private LogDoctorWebServer() {
    }

    public static void start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/api/analyze", LogDoctorWebServer::handleAnalyze);
            server.createContext("/api/health", exchange -> writeJson(exchange, 200, Map.of("status", "UP")));
            server.createContext("/", LogDoctorWebServer::handleStatic);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();

            System.out.printf("Log Doctor Web UI running at http://localhost:%d%n", port);
            System.out.println("Logs stay local; analysis uses the configured local Ollama instance.");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Log Doctor web server", e);
        }
    }

    private static void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            writeJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        byte[] requestBytes = exchange.getRequestBody().readNBytes(MAX_LOG_BYTES + 1);
        if (requestBytes.length > MAX_LOG_BYTES) {
            writeJson(exchange, 413, Map.of("error", "Log payload exceeds the 5 MB limit"));
            return;
        }

        try {
            AnalyzeRequest request = JSON.readValue(requestBytes, AnalyzeRequest.class);
            if (request.log() == null || request.log().isBlank()) {
                writeJson(exchange, 400, Map.of("error", "Log content is required"));
                return;
            }

            DiagnosisEngine engine = new DiagnosisEngine();
            String result = engine.analyzeToText(request.log());
            writeJson(exchange, 200, new AnalyzeResponse(result));
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of(
                    "error", "Analysis failed",
                    "detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
            ));
        }
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String resource = switch (path) {
            case "/", "/index.html" -> "/web/index.html";
            case "/app.css" -> "/web/app.css";
            case "/app.js" -> "/web/app.js";
            default -> null;
        };

        if (resource == null) {
            writeText(exchange, 404, "Not found", "text/plain; charset=utf-8");
            return;
        }

        try (InputStream in = LogDoctorWebServer.class.getResourceAsStream(resource)) {
            if (in == null) {
                writeText(exchange, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }

            String contentType = resource.endsWith(".html") ? "text/html; charset=utf-8"
                    : resource.endsWith(".css") ? "text/css; charset=utf-8"
                    : "application/javascript; charset=utf-8";
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private static void writeJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void writeText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record AnalyzeRequest(String log) {
    }

    private record AnalyzeResponse(String diagnosis) {
    }
}
