package io.github.mathias82.logdoctor.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.engine.LogBatchAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public final class LogDoctorWebServer {
    static final int MAX_LOG_BYTES = 5 * 1024 * 1024;
    private static final int JSON_OVERHEAD_BYTES = 64 * 1024;
    private static final int MAX_REQUEST_BYTES = MAX_LOG_BYTES + JSON_OVERHEAD_BYTES;
    private static final ObjectMapper JSON = new ObjectMapper();

    private LogDoctorWebServer() {}

    public static HttpServer start(int port) {
        return start(port, new DiagnosisEngine());
    }

    static HttpServer start(int port, DiagnosisEngine engine) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/api/analyze", exchange -> handleAnalyze(exchange, engine));
            server.createContext("/api/analyze/batch", exchange -> handleBatchAnalyze(exchange, engine));
            server.createContext("/api/health", LogDoctorWebServer::handleHealth);
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

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        writeJson(exchange, 200, Map.of("status", "UP"));
    }

    private static void handleAnalyze(HttpExchange exchange, DiagnosisEngine engine) throws IOException {
        handleLogRequest(exchange, log -> engine.analyzeStructured(log));
    }

    private static void handleBatchAnalyze(HttpExchange exchange, DiagnosisEngine engine) throws IOException {
        LogBatchAnalyzer batchAnalyzer = new LogBatchAnalyzer(engine);
        handleLogRequest(exchange, batchAnalyzer::analyze);
    }

    private static void handleLogRequest(HttpExchange exchange, LogAnalysis analysis) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        if (!isJsonRequest(exchange)) {
            writeJson(exchange, 415, Map.of("error", "Content-Type must be application/json"));
            return;
        }
        if (declaredRequestTooLarge(exchange)) {
            writeJson(exchange, 413, Map.of("error", "Request payload is too large"));
            return;
        }

        byte[] requestBytes;
        try (InputStream requestBody = exchange.getRequestBody()) {
            requestBytes = requestBody.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (requestBytes.length > MAX_REQUEST_BYTES) {
            writeJson(exchange, 413, Map.of("error", "Request payload is too large"));
            return;
        }

        try {
            AnalyzeRequest request = JSON.readValue(requestBytes, AnalyzeRequest.class);
            if (request.log() == null || request.log().isBlank()) {
                writeJson(exchange, 400, Map.of("error", "Log content is required"));
                return;
            }
            if (request.log().getBytes(StandardCharsets.UTF_8).length > MAX_LOG_BYTES) {
                writeJson(exchange, 413, Map.of("error", "Log content exceeds the 5 MB limit"));
                return;
            }
            writeJson(exchange, 200, analysis.analyze(request.log()));
        } catch (JsonProcessingException e) {
            writeJson(exchange, 400, Map.of("error", "Invalid JSON request"));
        } catch (RuntimeException e) {
            writeJson(exchange, 500, Map.of("error", "Analysis failed"));
        }
    }

    private static boolean requireMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        if (expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) return true;
        exchange.getResponseHeaders().set("Allow", expectedMethod);
        writeJson(exchange, 405, Map.of("error", "Method not allowed"));
        return false;
    }

    private static boolean isJsonRequest(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    private static boolean declaredRequestTooLarge(HttpExchange exchange) {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength == null) return false;
        try {
            return Long.parseLong(contentLength) > MAX_REQUEST_BYTES;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (!requireStaticGet(exchange)) return;
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
            writeResponse(exchange, 200, in.readAllBytes(), contentType(resource));
        }
    }

    private static boolean requireStaticGet(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) return true;
        exchange.getResponseHeaders().set("Allow", "GET");
        writeText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
        return false;
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".html")) return "text/html; charset=utf-8";
        if (resource.endsWith(".css")) return "text/css; charset=utf-8";
        return "application/javascript; charset=utf-8";
    }

    private static void writeJson(HttpExchange exchange, int status, Object value) throws IOException {
        writeResponse(exchange, status, JSON.writeValueAsBytes(value), "application/json; charset=utf-8");
    }

    private static void writeText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        writeResponse(exchange, status, text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void writeResponse(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        applyCommonHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        } finally {
            exchange.close();
        }
    }

    private static void applyCommonHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
    }

    @FunctionalInterface
    private interface LogAnalysis {
        Object analyze(String log);
    }

    private record AnalyzeRequest(String log) {}
}
