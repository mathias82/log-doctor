package io.github.mathias82.logdoctor.engine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a log stream into failure blocks, diagnoses each block independently,
 * groups repeated failures into incident fingerprints and derives a lightweight timeline.
 */
public final class LogBatchAnalyzer {
    private static final int MAX_INCIDENT_BLOCKS = 500;
    private static final long CORRELATION_WINDOW_SECONDS = 120;

    private static final Pattern OFFSET_TIMESTAMP = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2}))\\b");
    private static final Pattern LOCAL_TIMESTAMP = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?)\\b");
    private static final DateTimeFormatter LOCAL_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSSSSS]");
    private static final DateTimeFormatter LOCAL_COMMA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[,SSSSSSSSS]");

    private final DiagnosisEngine engine;

    public LogBatchAnalyzer(DiagnosisEngine engine) {
        this.engine = engine;
    }

    public BatchDiagnosisResult analyze(String log) {
        if (log == null || log.isBlank()) {
            return new BatchDiagnosisResult(0, 0, List.of(), List.of(), List.of());
        }

        List<FailureBlock> blocks = splitFailureBlocks(log);
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        List<DiagnosedEvent> diagnosedEvents = new ArrayList<>();

        for (FailureBlock block : blocks.stream().limit(MAX_INCIDENT_BLOCKS).toList()) {
            var result = engine.analyzeStructured(block.text());
            if ("NO_FAILURE".equals(result.status())) {
                continue;
            }
            String fingerprint = fingerprint(result);
            groups.computeIfAbsent(fingerprint, ignored -> new MutableGroup(result))
                    .addOccurrence(block.timestamp());
            diagnosedEvents.add(new DiagnosedEvent(fingerprint, result.type(), block.timestamp()));
        }

        List<IncidentGroup> incidents = groups.values().stream()
                .map(MutableGroup::snapshot)
                .sorted(Comparator.comparingInt(IncidentGroup::count).reversed())
                .toList();

        return new BatchDiagnosisResult(
                countLines(log),
                diagnosedEvents.size(),
                incidents,
                investigationOrder(incidents),
                correlations(diagnosedEvents)
        );
    }

    private static List<FailureBlock> splitFailureBlocks(String log) {
        String[] lines = log.split("\\R", -1);
        List<FailureBlock> blocks = new ArrayList<>();
        StringBuilder current = null;
        ParsedTimestamp currentTimestamp = null;

        for (String line : lines) {
            if (looksLikeFailureStart(line)) {
                if (current != null && !current.isEmpty()) {
                    blocks.add(new FailureBlock(current.toString(), currentTimestamp));
                }
                current = new StringBuilder(line);
                currentTimestamp = parseTimestamp(line);
            } else if (current != null) {
                if (looksLikeContinuation(line) || !line.isBlank()) {
                    current.append(System.lineSeparator()).append(line);
                }
            }
        }
        if (current != null && !current.isEmpty()) {
            blocks.add(new FailureBlock(current.toString(), currentTimestamp));
        }

        return blocks.isEmpty() ? List.of(new FailureBlock(log, parseTimestamp(log))) : blocks;
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

    private static ParsedTimestamp parseTimestamp(String text) {
        Matcher offsetMatcher = OFFSET_TIMESTAMP.matcher(text);
        if (offsetMatcher.find()) {
            String raw = offsetMatcher.group(1);
            try {
                OffsetDateTime parsed = OffsetDateTime.parse(normalizeOffset(raw));
                return new ParsedTimestamp(raw, parsed.toLocalDateTime());
            } catch (DateTimeParseException ignored) {
                // Fall through to local timestamp parsing.
            }
        }

        Matcher localMatcher = LOCAL_TIMESTAMP.matcher(text);
        if (localMatcher.find()) {
            String raw = localMatcher.group(1);
            String normalized = raw.replace('T', ' ');
            try {
                DateTimeFormatter formatter = normalized.contains(",") ? LOCAL_COMMA : LOCAL_SPACE;
                return new ParsedTimestamp(raw, LocalDateTime.parse(normalized, formatter));
            } catch (DateTimeParseException ignored) {
                return new ParsedTimestamp(raw, null);
            }
        }
        return null;
    }

    private static String normalizeOffset(String raw) {
        if (raw.endsWith("Z") || raw.matches(".*[+-]\\d{2}:\\d{2}$")) {
            return raw;
        }
        return raw.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
    }

    private static String fingerprint(DiagnosisEngine.DiagnosisResult result) {
        return normalize(result.type()) + "|" + normalize(result.category()) + "|" + normalizeRootCause(result.rootCause());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeRootCause(String value) {
        if (value == null) return "";
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

    private static List<IncidentCorrelation> correlations(List<DiagnosedEvent> events) {
        Map<String, MutableCorrelation> correlations = new LinkedHashMap<>();
        for (int i = 0; i < events.size() - 1; i++) {
            DiagnosedEvent first = events.get(i);
            DiagnosedEvent second = events.get(i + 1);
            if (first.fingerprint().equals(second.fingerprint()) || !withinCorrelationWindow(first, second)) {
                continue;
            }
            String key = first.fingerprint() + "->" + second.fingerprint();
            correlations.computeIfAbsent(key, ignored -> new MutableCorrelation(first.type(), second.type())).increment();
        }
        return correlations.values().stream()
                .map(MutableCorrelation::snapshot)
                .sorted(Comparator.comparingInt(IncidentCorrelation::occurrences).reversed())
                .limit(10)
                .toList();
    }

    private static boolean withinCorrelationWindow(DiagnosedEvent first, DiagnosedEvent second) {
        if (first.timestamp() == null || second.timestamp() == null
                || first.timestamp().value() == null || second.timestamp().value() == null) {
            return true;
        }
        long seconds = Math.abs(Duration.between(first.timestamp().value(), second.timestamp().value()).getSeconds());
        return seconds <= CORRELATION_WINDOW_SECONDS;
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
        private String firstSeen;
        private String lastSeen;

        private MutableGroup(DiagnosisEngine.DiagnosisResult sample) {
            this.sample = sample;
        }

        private void addOccurrence(ParsedTimestamp timestamp) {
            count++;
            if (timestamp == null) return;
            if (firstSeen == null) firstSeen = timestamp.raw();
            lastSeen = timestamp.raw();
        }

        private IncidentGroup snapshot() {
            return new IncidentGroup(
                    fingerprint(sample), count, sample.type(), sample.category(), sample.severity(), sample.confidence(),
                    sample.summary(), sample.rootCause(), sample.location(), sample.fixType(), sample.fix(),
                    sample.humanReviewRequired(), sample.llmUsed(), sample.evidence(), firstSeen, lastSeen
            );
        }
    }

    private static final class MutableCorrelation {
        private final String fromType;
        private final String toType;
        private int occurrences;

        private MutableCorrelation(String fromType, String toType) {
            this.fromType = fromType;
            this.toType = toType;
        }

        private void increment() { occurrences++; }

        private IncidentCorrelation snapshot() {
            return new IncidentCorrelation(fromType, toType, occurrences, "Observed consecutively within the correlation window");
        }
    }

    private record FailureBlock(String text, ParsedTimestamp timestamp) {}
    private record ParsedTimestamp(String raw, LocalDateTime value) {}
    private record DiagnosedEvent(String fingerprint, String type, ParsedTimestamp timestamp) {}

    public record BatchDiagnosisResult(
            int totalLines,
            int failureBlocks,
            List<IncidentGroup> incidents,
            List<String> investigationOrder,
            List<IncidentCorrelation> correlations
    ) {
        public int uniqueIncidents() { return incidents.size(); }
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
            String evidence,
            String firstSeen,
            String lastSeen
    ) {}

    public record IncidentCorrelation(
            String fromType,
            String toType,
            int occurrences,
            String reason
    ) {}
}
