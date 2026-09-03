package io.github.mathias82.logdoctor.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.engine.GroupingMetadata;
import io.github.mathias82.logdoctor.engine.LogBatchAnalyzer;
import io.github.mathias82.logdoctor.observability.RuntimeMetrics;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public final class LogDoctorWebServer {
    public static final String API_VERSION = "1";
    public static final String API_VERSION_HEADER = "X-Log-Doctor-Api-Version";
    static final int MAX_LOG_BYTES = 5 * 1024 * 1024;
    private static final int JSON_OVERHEAD_BYTES = 64 * 1024;
    private static final int MAX_JSON_BYTES_PER_LOG_BYTE = 6;
    private static final int MAX_REQUEST_BYTES = MAX_LOG_BYTES * MAX_JSON_BYTES_PER_LOG_BYTE + JSON_OVERHEAD_BYTES;
    private static final ObjectMapper JSON = new ObjectMapper();
    private LogDoctorWebServer() {}

    public static HttpServer start(int port) { return start("127.0.0.1", port, new DiagnosisEngine()); }
    public static HttpServer start(String host, int port) { return start(host, port, new DiagnosisEngine()); }
    static HttpServer start(int port, DiagnosisEngine engine) { return start("127.0.0.1", port, engine); }

    static HttpServer start(String host, int port, DiagnosisEngine engine) {
        try {
            RuntimeMetrics metrics = new RuntimeMetrics();
            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/analyze", exchange -> handleAnalyze(exchange, engine, metrics));
            server.createContext("/api/analyze/batch", exchange -> handleBatchAnalyze(exchange, engine, metrics));
            server.createContext("/api/health", LogDoctorWebServer::handleHealth);
            server.createContext("/api/metrics", exchange -> handleMetrics(exchange, metrics));
            server.createContext("/metrics", exchange -> handlePrometheusMetrics(exchange, metrics));
            server.createContext("/", LogDoctorWebServer::handleStatic);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            System.out.printf("Log Doctor Web UI listening on http://%s:%d%n", host, server.getAddress().getPort());
            System.out.println("Logs stay local; analysis uses the configured local Ollama instance.");
            return server;
        } catch (IOException e) { throw new IllegalStateException("Failed to start Log Doctor web server", e); }
    }

    private static void handleHealth(HttpExchange exchange) throws IOException { if (requireMethod(exchange, "GET")) writeJson(exchange, 200, Map.of("status", "UP", "apiVersion", API_VERSION)); }
    private static void handleMetrics(HttpExchange exchange, RuntimeMetrics metrics) throws IOException { if (requireMethod(exchange, "GET")) writeJson(exchange, 200, metrics.asMap()); }
    private static void handlePrometheusMetrics(HttpExchange exchange, RuntimeMetrics metrics) throws IOException { if (requireMethod(exchange, "GET")) writeText(exchange, 200, metrics.prometheusText(), "text/plain; version=0.0.4; charset=utf-8"); }
    private static void handleAnalyze(HttpExchange exchange, DiagnosisEngine engine, RuntimeMetrics metrics) throws IOException { handleLogRequest(exchange, engine::analyzeStructured, metrics); }
    private static void handleBatchAnalyze(HttpExchange exchange, DiagnosisEngine engine, RuntimeMetrics metrics) throws IOException { LogBatchAnalyzer analyzer = new LogBatchAnalyzer(engine); handleLogRequest(exchange, log -> addGroupingMetadata(analyzer.analyze(log)), metrics); }

    private static JsonNode addGroupingMetadata(LogBatchAnalyzer.BatchDiagnosisResult result) {
        ObjectNode root = JSON.valueToTree(result); JsonNode incidentsNode = root.get("incidents");
        if (!(incidentsNode instanceof ArrayNode incidents)) return root;
        for (JsonNode incidentNode : incidents) if (incidentNode instanceof ObjectNode incident) incident.set("grouping", JSON.valueToTree(GroupingMetadata.fromFingerprint(incident.path("fingerprint").asText(""))));
        return root;
    }

    private static void handleLogRequest(HttpExchange exchange, LogAnalysis analysis, RuntimeMetrics metrics) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        if (!isJsonRequest(exchange)) { writeJson(exchange, 415, Map.of("error", "Content-Type must be application/json")); return; }
        if (declaredRequestTooLarge(exchange)) { writeJson(exchange, 413, Map.of("error", "Request payload is too large")); return; }
        byte[] requestBytes;
        try (InputStream body = exchange.getRequestBody()) { requestBytes = body.readNBytes(MAX_REQUEST_BYTES + 1); }
        if (requestBytes.length > MAX_REQUEST_BYTES) { writeJson(exchange, 413, Map.of("error", "Request payload is too large")); return; }
        try {
            AnalyzeRequest request = JSON.readValue(requestBytes, AnalyzeRequest.class);
            if (request.log() == null || request.log().isBlank()) { writeJson(exchange, 400, Map.of("error", "Log content is required")); return; }
            if (request.log().getBytes(StandardCharsets.UTF_8).length > MAX_LOG_BYTES) { writeJson(exchange, 413, Map.of("error", "Log content exceeds the 5 MB limit")); return; }
            long started = System.nanoTime(); Object result = analysis.analyze(request.log());
            metrics.record(statusOf(result), llmUsedBy(result), System.nanoTime() - started, incidentCountOf(result));
            writeJson(exchange, 200, result);
        } catch (JsonProcessingException e) { writeJson(exchange, 400, Map.of("error", "Invalid JSON request")); }
        catch (RuntimeException e) { metrics.recordError(); writeJson(exchange, 500, Map.of("error", "Analysis failed")); }
    }

    private static int incidentCountOf(Object result) {
        JsonNode node = JSON.valueToTree(result); JsonNode incidentsNode = node.path("incidents");
        if (incidentsNode.isArray()) return incidentsNode.size();
        return "NO_FAILURE".equals(node.path("status").asText("")) ? 0 : 1;
    }

    private static String statusOf(Object result) {
        JsonNode node = JSON.valueToTree(result); if (node.has("status")) return node.path("status").asText("");
        JsonNode incidents = node.path("incidents"); if (incidents.isArray() && !incidents.isEmpty()) {
            boolean anyKnown = false; boolean anyUnknown = false;
            for (JsonNode incident : incidents) { String type = incident.path("type").asText(""); if ("UNKNOWN".equalsIgnoreCase(type)) anyUnknown = true; else anyKnown = true; }
            if (anyKnown) return "DIAGNOSED"; if (anyUnknown) return "UNKNOWN";
        }
        return "NO_FAILURE";
    }

    private static boolean llmUsedBy(Object result) {
        JsonNode node = JSON.valueToTree(result); if (node.path("llmUsed").asBoolean(false)) return true;
        JsonNode incidents = node.path("incidents"); if (incidents.isArray()) for (JsonNode incident : incidents) if (incident.path("llmUsed").asBoolean(false)) return true;
        return false;
    }

    private static boolean requireMethod(HttpExchange exchange, String expected) throws IOException { if (expected.equalsIgnoreCase(exchange.getRequestMethod())) return true; exchange.getResponseHeaders().set("Allow", expected); writeJson(exchange, 405, Map.of("error", "Method not allowed")); return false; }
    private static boolean isJsonRequest(HttpExchange exchange) { String ct = exchange.getRequestHeaders().getFirst("Content-Type"); return ct != null && ct.toLowerCase(Locale.ROOT).startsWith("application/json"); }
    private static boolean declaredRequestTooLarge(HttpExchange exchange) { String length = exchange.getRequestHeaders().getFirst("Content-Length"); if (length == null) return false; try { return Long.parseLong(length) > MAX_REQUEST_BYTES; } catch (NumberFormatException ignored) { return false; } }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (!requireStaticGet(exchange)) return;
        String resource = switch (exchange.getRequestURI().getPath()) { case "/", "/index.html" -> "/web/index.html"; case "/app.css" -> "/web/app.css"; case "/app-core.js" -> "/web/app-core.js"; case "/app.js" -> "/web/app.js"; default -> null; };
        if (resource == null) { writeText(exchange, 404, "Not found", "text/plain; charset=utf-8"); return; }
        try (InputStream in = LogDoctorWebServer.class.getResourceAsStream(resource)) { if (in == null) { writeText(exchange, 404, "Not found", "text/plain; charset=utf-8"); return; } writeResponse(exchange, 200, in.readAllBytes(), contentType(resource)); }
    }
    private static boolean requireStaticGet(HttpExchange exchange) throws IOException { if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) return true; exchange.getResponseHeaders().set("Allow", "GET"); writeText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8"); return false; }
    private static String contentType(String resource) { if (resource.endsWith(".html")) return "text/html; charset=utf-8"; if (resource.endsWith(".css")) return "text/css; charset=utf-8"; return "application/javascript; charset=utf-8"; }
    private static void writeJson(HttpExchange exchange, int status, Object value) throws IOException { writeResponse(exchange, status, JSON.writeValueAsBytes(value), "application/json; charset=utf-8"); }
    private static void writeText(HttpExchange exchange, int status, String text, String contentType) throws IOException { writeResponse(exchange, status, text.getBytes(StandardCharsets.UTF_8), contentType); }
    private static void writeResponse(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException { applyCommonHeaders(exchange); exchange.getResponseHeaders().set("Content-Type", contentType); exchange.getResponseHeaders().set("Cache-Control", "no-store"); exchange.sendResponseHeaders(status, body.length); try (var responseBody = exchange.getResponseBody()) { responseBody.write(body); } finally { exchange.close(); } }
    private static void applyCommonHeaders(HttpExchange exchange) { var headers = exchange.getResponseHeaders(); headers.set(API_VERSION_HEADER, API_VERSION); headers.set("X-Content-Type-Options", "nosniff"); headers.set("X-Frame-Options", "DENY"); headers.set("Referrer-Policy", "no-referrer"); headers.set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"); }
    @FunctionalInterface private interface LogAnalysis { Object analyze(String log); }
    private record AnalyzeRequest(String log) {}
}
