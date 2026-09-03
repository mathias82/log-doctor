package io.github.mathias82.logdoctor.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CiOutputFormatter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CiOutputFormatter() {}

    static String json(DiagnosisEngine.DiagnosisResult result) {
        return pretty(result);
    }

    static String sarif(DiagnosisEngine.DiagnosisResult result, Path source) {
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", "Log Doctor");
        driver.put("informationUri", "https://github.com/mathias82/log-doctor");
        driver.put("rules", "NO_FAILURE".equals(result.status()) ? List.of() : List.of(sarifRule(result)));

        Map<String, Object> tool = Map.of("driver", driver);
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tool", tool);
        run.put("results", "NO_FAILURE".equals(result.status()) ? List.of() : List.of(sarifResult(result, source)));

        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        sarif.put("version", "2.1.0");
        sarif.put("runs", List.of(run));
        return pretty(sarif);
    }

    static String github(DiagnosisEngine.DiagnosisResult result, Path source) {
        String title = "Log Doctor " + result.type();
        String message = result.summary() + " | " + result.rootCause();
        if ("NO_FAILURE".equals(result.status())) {
            return "::notice title=Log Doctor::" + escapeMessage(result.summary());
        }

        String level = "HIGH".equalsIgnoreCase(result.severity()) || "CRITICAL".equalsIgnoreCase(result.severity())
                ? "error" : "warning";
        StringBuilder properties = new StringBuilder("file=").append(escapeProperty(source.toString()));
        if (result.failureLine() != null && result.failureLine() > 0) {
            properties.append(",line=").append(result.failureLine());
        }
        properties.append(",title=").append(escapeProperty(title));
        return "::" + level + " " + properties + "::" + escapeMessage(message);
    }

    private static Map<String, Object> sarifRule(DiagnosisEngine.DiagnosisResult result) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", ruleId(result));
        rule.put("name", safe(result.type()));
        rule.put("shortDescription", Map.of("text", safe(result.summary())));
        rule.put("fullDescription", Map.of("text", safe(result.rootCause())));
        rule.put("help", Map.of("text", safe(result.fix())));
        rule.put("properties", Map.of(
                "category", safe(result.category()),
                "severity", safe(result.severity()),
                "matchConfidence", safe(result.matchConfidence()),
                "automaticExecutionAllowed", false));
        return rule;
    }

    private static Map<String, Object> sarifResult(DiagnosisEngine.DiagnosisResult result, Path source) {
        Map<String, Object> artifactLocation = Map.of("uri", source.toString().replace('\\', '/'));
        Map<String, Object> physicalLocation = new LinkedHashMap<>();
        physicalLocation.put("artifactLocation", artifactLocation);
        if (result.failureLine() != null && result.failureLine() > 0) {
            physicalLocation.put("region", Map.of("startLine", result.failureLine()));
        }

        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("ruleId", ruleId(result));
        finding.put("level", sarifLevel(result.severity()));
        finding.put("message", Map.of("text", safe(result.summary()) + " | " + safe(result.rootCause())));
        finding.put("locations", List.of(Map.of("physicalLocation", physicalLocation)));
        finding.put("properties", Map.of(
                "status", safe(result.status()),
                "source", safe(result.source()),
                "fixPolicy", safe(result.fixPolicy()),
                "matchScore", result.matchScore()));
        return finding;
    }

    private static String ruleId(DiagnosisEngine.DiagnosisResult result) {
        String raw = safe(result.type()).isBlank() ? "UNKNOWN" : result.type();
        return "LOGDOCTOR-" + raw.toUpperCase().replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String sarifLevel(String severity) {
        return switch (safe(severity).toUpperCase()) {
            case "HIGH", "CRITICAL" -> "error";
            case "MEDIUM" -> "warning";
            default -> "note";
        };
    }

    private static String pretty(Object value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize diagnosis", e);
        }
    }

    private static String escapeMessage(String value) {
        return safe(value)
                .replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }

    private static String escapeProperty(String value) {
        return escapeMessage(value)
                .replace(":", "%3A")
                .replace(",", "%2C");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
