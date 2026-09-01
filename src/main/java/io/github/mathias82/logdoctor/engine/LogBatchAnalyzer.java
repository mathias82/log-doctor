package io.github.mathias82.logdoctor.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Splits a log stream into failure blocks, diagnoses each block independently and
 * groups repeated failures into incident fingerprints.
 */
public final class LogBatchAnalyzer {
    private static final int MAX_INCIDENT_BLOCKS = 500;

    private final DiagnosisEngine engine;

    public LogBatchAnalyzer(DiagnosisEngine engine) {
        this.engine = engine;
    }

    public BatchDiagnosisResult analyze(String log) {
        if (log == null || log.isBlank()) {
            return new BatchDiagnosisResult(0, 0, List.of(), List.of());
        }

        List<String> blocks = splitFailureBlocks(log);
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        int diagnosed = 0;

        for (String block : blocks.stream().limit(MAX_INCIDENT_BLOCKS).toList()) {
            var result = engine.analyzeStructured(block);
            if ("NO_FAILURE".equals(result.status())) {
                continue;
            }
            diagnosed++;
            String fingerprint = fingerprint(result);
            groups.computeIfAbsent(fingerprint, ignored -> new MutableGroup(result)).increment();
        }

        List<IncidentGroup> incidents = groups.values().stream()
                .map(MutableGroup::snapshot)
                .sorted(Comparator.comparingInt(IncidentGroup::count).reversed())
                .toList();

        return new BatchDiagnosisResult(
                countLines(log),
                diagnosed,
                incidents,
                investigationOrder(incidents)
        );
    }

    private static List<String> splitFailureBlocks(String log) {
        String[] lines = log.split("\\R", -1);
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;

        for (String line : lines) {
            if (looksLikeFailureStart(line)) {
                if (current != null && !current.isEmpty()) {
                    blocks.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                if (looksLikeContinuation(line)) {
                    current.append(System.lineSeparator()).append(line);
                } else if (!line.isBlank()) {
                    current.append(System.lineSeparator()).append(line);
                }
            }
        }
        if (current != null && !current.isEmpty()) {
            blocks.add(current.toString());
        }

        // Keep the original single-diagnosis behavior useful for small/atypical logs.
        return blocks.isEmpty() ? List.of(log) : blocks;
    }

    private static boolean looksLikeFailureStart(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains(" exception")
                || lower.startsWith("exception")
                || lower.contains("error ")
                || lower.startsWith("error")
                || lower.contains("caused by:")
                || lower.contains("outofmemoryerror")
                || lower.contains("timeout")
                || lower.contains("deadlock");
    }

    private static boolean looksLikeContinuation(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("at ")
                || trimmed.startsWith("...")
                || trimmed.startsWith("Caused by:")
                || trimmed.startsWith("Suppressed:")
                || line.isBlank();
    }

    private static String fingerprint(DiagnosisEngine.DiagnosisResult result) {
        return normalize(result.type()) + "|" + normalize(result.category()) + "|" + normalizeRootCause(result.rootCause());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeRootCause(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("0x[0-9a-f]+", "<hex>")
                .replaceAll("\\b\\d+\\b", "<n>")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int countLines(String log) {
        return log.isEmpty() ? 0 : log.split("\\R", -1).length;
    }

    private static List<String> investigationOrder(List<IncidentGroup> incidents) {
        return incidents.stream()
                .sorted(Comparator
                        .comparingInt((IncidentGroup group) -> severityRank(group.severity())).reversed()
                        .thenComparing(Comparator.comparingInt(IncidentGroup::count).reversed()))
                .limit(5)
                .map(group -> group.type() + " — " + group.summary())
                .toList();
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH", "ERROR" -> 3;
            case "MEDIUM", "WARN" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static final class MutableGroup {
        private final DiagnosisEngine.DiagnosisResult sample;
        private int count;

        private MutableGroup(DiagnosisEngine.DiagnosisResult sample) {
            this.sample = sample;
        }

        private void increment() {
            count++;
        }

        private IncidentGroup snapshot() {
            return new IncidentGroup(
                    fingerprint(sample), count, sample.type(), sample.category(), sample.severity(), sample.confidence(),
                    sample.summary(), sample.rootCause(), sample.location(), sample.fixType(), sample.fix(),
                    sample.humanReviewRequired(), sample.llmUsed(), sample.evidence()
            );
        }
    }

    public record BatchDiagnosisResult(
            int totalLines,
            int failureBlocks,
            List<IncidentGroup> incidents,
            List<String> investigationOrder
    ) {
        public int uniqueIncidents() {
            return incidents.size();
        }
    }

    public record IncidentGroup(
            String fingerprint,
            int count,
            String type,
            String category,
            String severity,
            String confidence,
            String summary,
            String rootCause,
            String location,
            String fixType,
            String fix,
            boolean humanReviewRequired,
            boolean llmUsed,
            String evidence
    ) {}
}
