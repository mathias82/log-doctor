package io.github.mathias82.logdoctor.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;

import java.nio.file.Path;

final class CiOutputFormatter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CiOutputFormatter() {}

    static String json(DiagnosisEngine.DiagnosisResult result) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize diagnosis", e);
        }
    }

    static String github(DiagnosisEngine.DiagnosisResult result, Path source) {
        String title = "Log Doctor " + result.type();
        String message = result.summary() + " | " + result.rootCause();
        if ("NO_FAILURE".equals(result.status())) {
            return "::notice title=Log Doctor::" + escapeMessage(result.summary());
        }

        String level = "HIGH".equalsIgnoreCase(result.severity()) ? "error" : "warning";
        StringBuilder properties = new StringBuilder("file=").append(escapeProperty(source.toString()));
        if (result.failureLine() != null && result.failureLine() > 0) {
            properties.append(",line=").append(result.failureLine());
        }
        properties.append(",title=").append(escapeProperty(title));
        return "::" + level + " " + properties + "::" + escapeMessage(message);
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
