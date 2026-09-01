package io.github.mathias82.logdoctor.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
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
    static final int MAX_INCIDENT_BLOCKS = 500;
    static final long CORRELATION_WINDOW_SECONDS = 120;

    private static final Pattern OFFSET_TIMESTAMP = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2}))\\b");
    private static final Pattern LOCAL_TIMESTAMP = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?)\\b");
    private static final Pattern LOG_LEVEL = Pattern.compile("(?i)\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    private final DiagnosisEngine engine;

    public LogBatchAnalyzer(DiagnosisEngine engine) {
        this.engine = engine;
    }

    public BatchDiagnosisResult analyze(String log) {
        if (log == null || log.isBlank()) {
            return new BatchDiagnosisResult(0, 0, 0, false, List.of(), List.of(), List.of());
        }

        List<FailureBlock> blocks = splitFailureBlocks(log);
        int detectedFailureBlocks = blocks.size();
        boolean truncated = detectedFailureBlocks > MAX_INCIDENT_BLOCKS;
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
                detectedFailureBlocks,
                truncated,
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
            if (current == null) {
                if (looksLikeFailureStart(line)) {
                    current = new StringBuilder(line);
                    currentTimestamp = parseTimestamp(line);
                }
                continue;
            }

            if (looksLikeTopLevelFailureStart(line)) {
                blocks.add(new FailureBlock(current.toString(), currentTimestamp));
                current = new StringBuilder(line);
                currentTimestamp = parseTimestamp(line);
            } else if (looksLikeNonFailureLogBoundary(line)) {
                blocks.add(new FailureBlock(current.toString(), currentTimestamp));
                current = null;
                currentTimestamp = null;
            } else {
                current.append(System.lineSeparator()).append(line);
            }
        }

        if (current != null && !current.isEmpty()) {
            blocks.add(new FailureBlock(current.toString(), currentTimestamp));
        }

        return blocks.isEmpty() ? List.of(new FailureBlock(log, parseTimestamp(log))) : blocks;
    }

    private static boolean looksLikeFailureStart(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return looksLikeTopLevelFailureStart(line)
                || lower.contains(" exception")
                || lower.startsWith("exception")
                || lower.contains("caused by:")
                || lower.contains("outofmemoryerror")
                || lower.contains("sockettimeoutexception")
                || lower.contains("deadlock");
    }

    private static boolean looksLikeTopLevelFailureStart(String line) {
        String trimmed = line.stripLeading();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("error ") || lower.startsWith("error:")
                || lower.startsWith("fatal ") || lower.startsWith("fatal:")) {
            return true;
        }
        if (parseTimestamp(line) == null) {
            return false;
        }
        String level = logLevel(line);
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    private static boolean looksLikeNonFailureLogBoundary(String line) {
        if (parseTimestamp(line) == null || looksLikeContinuation(line)) {
            return false;
        }
        String level = logLevel(line);
        return "TRACE".equals(level)
                || "DEBUG".equals(level)
                || "INFO".equals(level)
                || "WARN".equals(level)
                || "WARNING".equals(level);
    }

    private static String logLevel(String line) {
        Matcher matcher = LOG_LEVEL.matcher(line);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
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
                return ParsedTimestamp.offset(raw, parsed.toInstant());
            } catch (DateTimeParseException ignored) {
                // Fall through to local timestamp parsing.
            }
        }

        Matcher localMatcher = LOCAL_TIMESTAMP.matcher(text);
        if (localMatcher.find()) {
            String raw = localMatcher.group(1);
            String normalized = raw.replace('T', ' ').replace(',', '.');
            try {
                return ParsedTimestamp.local(raw, LocalDateTime.parse(normalized, LOCAL_TIMESTAMP_FORMATTER));
            } catch (DateTimeParseException ignored) {
                return new ParsedTimestamp(raw, null, null);
            }
        }
        return null;
    }

    private static String normalizeOffset(String raw) {
        if (raw.endsWith("Z") || raw.matches(".*[+-]\\d{2}:\\d{2}$")) return raw;
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
        return UUID.matcher(value.toLowerCase(Locale.ROOT))
                .replaceAll("<uuid>")
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
        Map<String, MutableCorrelation> found = new LinkedHashMap<>();
        for (int i = 0; i < events.size() - 1; i++) {
            DiagnosedEvent first = events.get(i);
            DiagnosedEvent second = events.get(i + 1);
            if (first.fingerprint().equals(second.fingerprint()) || !withinCorrelationWindow(first, second)) continue;
            String key = first.fingerprint() + "->" + second.fingerprint();
            found.computeIfAbsent(key, ignored -> new MutableCorrelation(first.type(), second.type())).increment();
        }
        return found.values().stream()
                .map(MutableCorrelation::snapshot)
                .sorted(Comparator.comparingInt(IncidentCorrelation::occurrences).reversed())
                .limit(10)
                .toList();
    }

    private static boolean withinCorrelationWindow(DiagnosedEvent first, DiagnosedEvent second) {
        Long seconds = secondsBetween(first.timestamp(), second.timestamp());
        return seconds != null && seconds >= 0 && seconds <= CORRELATION_WINDOW_SECONDS;
    }

    private static Long secondsBetween(ParsedTimestamp first, ParsedTimestamp second) {
        if (first == null || second == null) return null;
        if (first.instant() != null && second.instant() != null) {
            return Duration.between(first.instant(), second.instant()).getSeconds();
        }
        if (first.localDateTime() != null && second.localDateTime() != null) {
            return Duration.between(first.localDateTime(), second.localDateTime()).getSeconds();
        }
        return null;
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
        private ParsedTimestamp firstSeen;
        private ParsedTimestamp lastSeen;

        private MutableGroup(DiagnosisEngine.DiagnosisResult sample) { this.sample = sample; }

        private void addOccurrence(ParsedTimestamp timestamp) {
            count++;
            if (timestamp == null) return;
            if (firstSeen == null) {
                firstSeen = timestamp;
                lastSeen = timestamp;
                return;
            }
            Long fromFirst = secondsBetween(firstSeen, timestamp);
            if (fromFirst != null && fromFirst < 0) {
                firstSeen = timestamp;
            }
            Long fromLast = secondsBetween(lastSeen, timestamp);
            if (fromLast == null || fromLast >= 0) {
                lastSeen = timestamp;
            }
        }

        private IncidentGroup snapshot() {
            return new IncidentGroup(
                    fingerprint(sample), count, sample.type(), sample.category(), sample.severity(), sample.confidence(),
                    sample.summary(), sample.rootCause(), sample.location(), sample.fixType(), sample.fix(),
                    sample.humanReviewRequired(), sample.llmUsed(), sample.evidence(),
                    firstSeen == null ? null : firstSeen.raw(),
                    lastSeen == null ? null : lastSeen.raw()
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
            return new IncidentCorrelation(fromType, toType, occurrences,
                    "Observed consecutively within " + CORRELATION_WINDOW_SECONDS + " seconds using comparable timestamps");
        }
    }

    private record FailureBlock(String text, ParsedTimestamp timestamp) {}

    private record ParsedTimestamp(String raw, Instant instant, LocalDateTime localDateTime) {
        private static ParsedTimestamp offset(String raw, Instant instant) {
            return new ParsedTimestamp(raw, instant, null);
        }

        private static ParsedTimestamp local(String raw, LocalDateTime localDateTime) {
            return new ParsedTimestamp(raw, null, localDateTime);
        }
    }

    private record DiagnosedEvent(String fingerprint, String type, ParsedTimestamp timestamp) {}

    public record BatchDiagnosisResult(
            int totalLines,
            int failureBlocks,
            int detectedFailureBlocks,
            boolean truncated,
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
