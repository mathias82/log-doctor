package io.github.mathias82.logdoctor.web;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    private LogDoctorWebServer() {}

    public static HttpServer start(int port) {
        return start(port, new DiagnosisEngine());
    }

    static HttpServer start(int port, DiagnosisEngine engine) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/api/analyze", exchange -> handleAnalyze(exchange, engine));
            server.createContext("/api/health", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    writeJson(exchange, 405, Map.of("error", "Method not allowed"));
                    return;
                }
                writeJson(exchange, 200, Map.of("status", "UP"));
            });
            server.createContext("/", LogDoctorWebServer::handleStatic);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            System.out.printf("Log Doctor Web UI running at http://localhost:%d%n", server.getAddress().getPort());
            System.out.println("Logs stay local; analysis uses the configured local Ollama instance.");
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Log Doctor web server", e);
        }
    }

    private static void handleAnalyze(HttpExchange exchange, DiagnosisEngine engine) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            writeJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            writeJson(exchange, 415, Map.of("error", "Content-Type must be application/json"));
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
            writeJson(exchange, 200, engine.analyzeStructured(request.log()));
        } catch (JsonProcessingException e) {
            writeJson(exchange, 400, Map.of("error", "Invalid JSON request"));
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "Analysis failed"));
        }
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            writeText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }

        String resource = switch (exchange.getRequestURI().getPath()) {
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
            applyCommonHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private static void writeJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        applyCommonHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void writeText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        applyCommonHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void applyCommonHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
    }

    private record AnalyzeRequest(String log) {}
}
