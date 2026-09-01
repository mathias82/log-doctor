package io.github.mathias82.logdoctor.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a log stream into failure blocks, diagnoses each block independently,
 * groups repeated failures into incident fingerprints and derives timeline insights.
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
            return new BatchDiagnosisResult(0, 0, 0, false, List.of(), List.of(), List.of(), List.of(), List.of(), emptyReport());
        }

        List<FailureBlock> blocks = splitFailureBlocks(log);
        int detectedFailureBlocks = blocks.size();
        boolean truncated = detectedFailureBlocks > MAX_INCIDENT_BLOCKS;
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        List<DiagnosedEvent> diagnosedEvents = new ArrayList<>();

        for (FailureBlock block : blocks.stream().limit(MAX_INCIDENT_BLOCKS).toList()) {
            var result = engine.analyzeStructured(block.text());
            if ("NO_FAILURE".equals(result.status())) continue;
            String fingerprint = fingerprint(result);
            groups.computeIfAbsent(fingerprint, ignored -> new MutableGroup(result)).addOccurrence(block.timestamp());
            diagnosedEvents.add(new DiagnosedEvent(fingerprint, result.type(), result.severity(), block.timestamp()));
        }

        List<IncidentGroup> incidents = groups.values().stream()
                .map(MutableGroup::snapshot)
                .sorted(Comparator.comparingInt(IncidentGroup::count).reversed())
                .toList();
        List<String> investigationOrder = investigationOrder(incidents);
        List<IncidentCorrelation> correlations = correlations(diagnosedEvents);
        List<RootCauseChain> rootCauseChains = rootCauseChains(correlations, incidents);
        List<IncidentSpike> spikes = spikes(diagnosedEvents);
        String report = reportMarkdown(
                countLines(log), diagnosedEvents.size(), detectedFailureBlocks, truncated,
                incidents, investigationOrder, rootCauseChains, spikes
        );

        return new BatchDiagnosisResult(
                countLines(log), diagnosedEvents.size(), detectedFailureBlocks, truncated,
                incidents, investigationOrder, correlations, rootCauseChains, spikes, report
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
        if (current != null && !current.isEmpty()) blocks.add(new FailureBlock(current.toString(), currentTimestamp));
        return blocks.isEmpty() ? List.of(new FailureBlock(log, parseTimestamp(log))) : blocks;
    }

    private static boolean looksLikeFailureStart(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return looksLikeTopLevelFailureStart(line)
                || lower.contains(" exception") || lower.startsWith("exception")
                || lower.contains("caused by:") || lower.contains("outofmemoryerror")
                || lower.contains("sockettimeoutexception") || lower.contains("deadlock");
    }

    private static boolean looksLikeTopLevelFailureStart(String line) {
        String lower = line.stripLeading().toLowerCase(Locale.ROOT);
        if (lower.startsWith("error ") || lower.startsWith("error:")
                || lower.startsWith("fatal ") || lower.startsWith("fatal:")) return true;
        if (parseTimestamp(line) == null) return false;
        String level = logLevel(line);
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    private static boolean looksLikeNonFailureLogBoundary(String line) {
        if (parseTimestamp(line) == null || looksLikeContinuation(line)) return false;
        String level = logLevel(line);
        return "TRACE".equals(level) || "DEBUG".equals(level) || "INFO".equals(level)
                || "WARN".equals(level) || "WARNING".equals(level);
    }

    private static String logLevel(String line) {
        Matcher matcher = LOG_LEVEL.matcher(line);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private static boolean looksLikeContinuation(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("at ") || trimmed.startsWith("...")
                || trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:") || line.isBlank();
    }

    private static ParsedTimestamp parseTimestamp(String text) {
        Matcher offsetMatcher = OFFSET_TIMESTAMP.matcher(text);
        if (offsetMatcher.find()) {
            String raw = offsetMatcher.group(1);
            try {
                return ParsedTimestamp.offset(raw, OffsetDateTime.parse(normalizeOffset(raw)).toInstant());
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
        return UUID.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("<uuid>")
                .replaceAll("0x[0-9a-f]+", "<hex>")
                .replaceAll("\\b\\d+\\b", "<n>")
                .replaceAll("\\s+", " ").trim();
    }

    private static int countLines(String log) {
        return log.isEmpty() ? 0 : log.split("\\R", -1).length;
    }

    private static List<String> investigationOrder(List<IncidentGroup> incidents) {
        return incidents.stream()
                .sorted(Comparator.comparingInt((IncidentGroup g) -> severityRank(g.severity())).reversed()
                        .thenComparing(Comparator.comparingInt(IncidentGroup::count).reversed()))
                .limit(5).map(g -> g.type() + " — " + g.summary()).toList();
    }

    private static List<IncidentCorrelation> correlations(List<DiagnosedEvent> events) {
        Map<String, MutableCorrelation> found = new LinkedHashMap<>();
        for (int i = 0; i < events.size() - 1; i++) {
            DiagnosedEvent first = events.get(i), second = events.get(i + 1);
            if (first.fingerprint().equals(second.fingerprint()) || !withinCorrelationWindow(first, second)) continue;
            String key = first.fingerprint() + "->" + second.fingerprint();
            found.computeIfAbsent(key, ignored -> new MutableCorrelation(first.type(), second.type())).increment();
        }
        return found.values().stream().map(MutableCorrelation::snapshot)
                .sorted(Comparator.comparingInt(IncidentCorrelation::occurrences).reversed()).limit(10).toList();
    }

    private static List<RootCauseChain> rootCauseChains(List<IncidentCorrelation> correlations, List<IncidentGroup> incidents) {
        Map<String, Integer> severityByType = new HashMap<>();
        for (IncidentGroup incident : incidents) {
            severityByType.merge(incident.type(), severityRank(incident.severity()), Math::max);
        }
        return correlations.stream().map(correlation -> {
                    int fromSeverity = severityByType.getOrDefault(correlation.fromType(), 0);
                    int toSeverity = severityByType.getOrDefault(correlation.toType(), 0);
                    int score = Math.min(100, 30 + correlation.occurrences() * 15 + fromSeverity * 5 + toSeverity * 10);
                    String confidence = score >= 80 ? "HIGH" : score >= 60 ? "MEDIUM" : "LOW";
                    return new RootCauseChain(
                            correlation.fromType(), correlation.toType(), correlation.occurrences(), score, confidence,
                            "Candidate dependency chain scored from temporal adjacency, repetition and incident severity; not proven causation."
                    );
                })
                .sorted(Comparator.comparingInt(RootCauseChain::score).reversed()
                        .thenComparing(Comparator.comparingInt(RootCauseChain::occurrences).reversed()))
                .limit(10).toList();
    }

    private static List<IncidentSpike> spikes(List<DiagnosedEvent> events) {
        Map<String, SpikeSeries> series = new LinkedHashMap<>();
        for (DiagnosedEvent event : events) {
            if (event.timestamp() == null) continue;
            Long minute = event.timestamp().minuteIndex();
            String basis = event.timestamp().basis();
            if (minute == null || basis == null) continue;
            String key = event.fingerprint() + "|" + basis;
            series.computeIfAbsent(key, ignored -> new SpikeSeries(event.type())).add(minute, event.timestamp().raw());
        }

        return series.values().stream().map(SpikeSeries::detect).filter(spike -> spike != null)
                .sorted(Comparator.comparingInt(IncidentSpike::score).reversed()
                        .thenComparing(Comparator.comparingDouble(IncidentSpike::multiplier).reversed()))
                .limit(10).toList();
    }

    private static boolean withinCorrelationWindow(DiagnosedEvent first, DiagnosedEvent second) {
        Long seconds = secondsBetween(first.timestamp(), second.timestamp());
        return seconds != null && seconds >= 0 && seconds <= CORRELATION_WINDOW_SECONDS;
    }

    private static Long secondsBetween(ParsedTimestamp first, ParsedTimestamp second) {
        if (first == null || second == null) return null;
        if (first.instant() != null && second.instant() != null) return Duration.between(first.instant(), second.instant()).getSeconds();
        if (first.localDateTime() != null && second.localDateTime() != null) return Duration.between(first.localDateTime(), second.localDateTime()).getSeconds();
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

    private static String reportMarkdown(
            int totalLines, int failureBlocks, int detectedFailureBlocks, boolean truncated,
            List<IncidentGroup> incidents, List<String> investigationOrder,
            List<RootCauseChain> chains, List<IncidentSpike> spikes
    ) {
        StringBuilder out = new StringBuilder("# Log Doctor Incident Report\n\n")
                .append("## Summary\n\n")
                .append("- Total log lines: ").append(totalLines).append('\n')
                .append("- Diagnosed failure blocks: ").append(failureBlocks).append('\n')
                .append("- Detected failure blocks: ").append(detectedFailureBlocks).append('\n')
                .append("- Unique incidents: ").append(incidents.size()).append('\n')
                .append("- Analysis truncated: ").append(truncated).append("\n\n");

        out.append("## Investigation order\n\n");
        if (investigationOrder.isEmpty()) out.append("No failure detected.\n\n");
        else for (int i = 0; i < investigationOrder.size(); i++) out.append(i + 1).append(". ").append(investigationOrder.get(i)).append('\n');

        out.append("\n## Root-cause chain candidates\n\n");
        if (chains.isEmpty()) out.append("No timestamp-supported chain candidate detected.\n");
        else for (RootCauseChain chain : chains) out.append("- **").append(chain.fromType()).append(" → ").append(chain.toType())
                .append("** — score ").append(chain.score()).append("/100, ").append(chain.confidence())
                .append(", observed ").append(chain.occurrences()).append("×\n");

        out.append("\n## Spikes\n\n");
        if (spikes.isEmpty()) out.append("No significant per-minute spike detected.\n");
        else for (IncidentSpike spike : spikes) out.append("- **").append(spike.type()).append("** — ")
                .append(spike.count()).append(" events near ").append(spike.windowStart())
                .append(", ").append(String.format(Locale.ROOT, "%.1fx", spike.multiplier())).append(" baseline, score ")
                .append(spike.score()).append("/100\n");

        out.append("\n## Incident groups\n\n");
        for (IncidentGroup incident : incidents) {
            out.append("### ").append(incident.count()).append("× ").append(incident.type()).append("\n\n")
                    .append("- Severity: ").append(incident.severity()).append('\n')
                    .append("- Category: ").append(incident.category()).append('\n')
                    .append("- Confidence: ").append(incident.confidence()).append('\n')
                    .append("- First seen: ").append(incident.firstSeen() == null ? "unknown" : incident.firstSeen()).append('\n')
                    .append("- Last seen: ").append(incident.lastSeen() == null ? "unknown" : incident.lastSeen()).append('\n')
                    .append("- Summary: ").append(incident.summary()).append('\n')
                    .append("- Root cause: ").append(incident.rootCause()).append('\n')
                    .append("- Remediation: ").append(incident.fix()).append("\n\n");
        }
        out.append("_Root-cause chain scores are heuristic evidence, not proof of causation._\n");
        return out.toString();
    }

    private static String emptyReport() {
        return "# Log Doctor Incident Report\n\nNo log content was provided.\n";
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
            if (firstSeen == null) { firstSeen = timestamp; lastSeen = timestamp; return; }
            Long fromFirst = secondsBetween(firstSeen, timestamp);
            if (fromFirst != null && fromFirst < 0) firstSeen = timestamp;
            Long fromLast = secondsBetween(lastSeen, timestamp);
            if (fromLast == null || fromLast >= 0) lastSeen = timestamp;
        }
        private IncidentGroup snapshot() {
            return new IncidentGroup(fingerprint(sample), count, sample.type(), sample.category(), sample.severity(), sample.confidence(),
                    sample.summary(), sample.rootCause(), sample.location(), sample.fixType(), sample.fix(), sample.humanReviewRequired(),
                    sample.llmUsed(), sample.evidence(), firstSeen == null ? null : firstSeen.raw(), lastSeen == null ? null : lastSeen.raw());
        }
    }

    private static final class MutableCorrelation {
        private final String fromType;
        private final String toType;
        private int occurrences;
        private MutableCorrelation(String fromType, String toType) { this.fromType = fromType; this.toType = toType; }
        private void increment() { occurrences++; }
        private IncidentCorrelation snapshot() {
            return new IncidentCorrelation(fromType, toType, occurrences,
                    "Observed consecutively within " + CORRELATION_WINDOW_SECONDS + " seconds using comparable timestamps");
        }
    }

    private static final class SpikeSeries {
        private final String type;
        private final Map<Long, MinuteBucket> buckets = new HashMap<>();
        private int total;
        private long minMinute = Long.MAX_VALUE;
        private long maxMinute = Long.MIN_VALUE;
        private SpikeSeries(String type) { this.type = type; }
        private void add(long minute, String raw) {
            total++;
            minMinute = Math.min(minMinute, minute);
            maxMinute = Math.max(maxMinute, minute);
            buckets.computeIfAbsent(minute, ignored -> new MinuteBucket(raw)).count++;
        }
        private IncidentSpike detect() {
            if (buckets.size() < 2 || maxMinute <= minMinute) return null;
            Map.Entry<Long, MinuteBucket> peak = buckets.entrySet().stream().max(Map.Entry.comparingByValue(Comparator.comparingInt(b -> b.count))).orElseThrow();
            long spanMinutes = maxMinute - minMinute + 1;
            double baseline = total / (double) spanMinutes;
            double multiplier = baseline == 0 ? 0 : peak.getValue().count / baseline;
            if (peak.getValue().count < 3 || multiplier < 2.0) return null;
            int score = Math.min(100, (int) Math.round(25 + peak.getValue().count * 8 + multiplier * 10));
            return new IncidentSpike(type, peak.getValue().raw, peak.getValue().count, baseline, multiplier, score);
        }
    }

    private static final class MinuteBucket {
        private final String raw;
        private int count;
        private MinuteBucket(String raw) { this.raw = raw; }
    }

    private record FailureBlock(String text, ParsedTimestamp timestamp) {}
    private record ParsedTimestamp(String raw, Instant instant, LocalDateTime localDateTime) {
        private static ParsedTimestamp offset(String raw, Instant instant) { return new ParsedTimestamp(raw, instant, null); }
        private static ParsedTimestamp local(String raw, LocalDateTime localDateTime) { return new ParsedTimestamp(raw, null, localDateTime); }
        private String basis() { return instant != null ? "OFFSET" : localDateTime != null ? "LOCAL" : null; }
        private Long minuteIndex() {
            if (instant != null) return instant.getEpochSecond() / 60;
            if (localDateTime != null) return ChronoUnit.MINUTES.between(LocalDateTime.of(1970, 1, 1, 0, 0), localDateTime);
            return null;
        }
    }
    private record DiagnosedEvent(String fingerprint, String type, String severity, ParsedTimestamp timestamp) {}

    public record BatchDiagnosisResult(
            int totalLines,
            int failureBlocks,
            int detectedFailureBlocks,
            boolean truncated,
            List<IncidentGroup> incidents,
            List<String> investigationOrder,
            List<IncidentCorrelation> correlations,
            List<RootCauseChain> rootCauseChains,
            List<IncidentSpike> spikes,
            String reportMarkdown
    ) { public int uniqueIncidents() { return incidents.size(); } }

    public record IncidentGroup(
            String fingerprint, int count, String type, String category, String severity, String confidence,
            String summary, String rootCause, String location, String fixType, String fix,
            boolean humanReviewRequired, boolean llmUsed, String evidence, String firstSeen, String lastSeen
    ) {}

    public record IncidentCorrelation(String fromType, String toType, int occurrences, String reason) {}
    public record RootCauseChain(String fromType, String toType, int occurrences, int score, String confidence, String reason) {}
    public record IncidentSpike(String type, String windowStart, int count, double baselinePerMinute, double multiplier, int score) {}
}
