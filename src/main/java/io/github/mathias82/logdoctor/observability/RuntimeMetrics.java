package io.github.mathias82.logdoctor.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight in-process operational metrics for Log Doctor.
 *
 * <p>The counters intentionally avoid raw log content, evidence, prompts, or other
 * potentially sensitive values. They describe analysis behavior only.</p>
 */
public final class RuntimeMetrics {
    private final LongAdder analyses = new LongAdder();
    private final LongAdder deterministicDiagnoses = new LongAdder();
    private final LongAdder unknownDiagnoses = new LongAdder();
    private final LongAdder noFailure = new LongAdder();
    private final LongAdder llmUsed = new LongAdder();
    private final LongAdder analysisErrors = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong maxLatencyNanos = new AtomicLong();

    public void record(String status, boolean usedLlm, long elapsedNanos) {
        analyses.increment();
        totalLatencyNanos.add(Math.max(0L, elapsedNanos));
        maxLatencyNanos.accumulateAndGet(Math.max(0L, elapsedNanos), Math::max);

        if ("DIAGNOSED".equals(status)) {
            deterministicDiagnoses.increment();
        } else if ("UNKNOWN".equals(status)) {
            unknownDiagnoses.increment();
        } else if ("NO_FAILURE".equals(status)) {
            noFailure.increment();
        }
        if (usedLlm) {
            llmUsed.increment();
        }
    }

    public void recordError() {
        analysisErrors.increment();
    }

    public Snapshot snapshot() {
        long count = analyses.sum();
        long total = totalLatencyNanos.sum();
        return new Snapshot(
                count,
                deterministicDiagnoses.sum(),
                unknownDiagnoses.sum(),
                noFailure.sum(),
                llmUsed.sum(),
                analysisErrors.sum(),
                nanosToMillis(count == 0 ? 0 : total / count),
                nanosToMillis(maxLatencyNanos.get()));
    }

    public Map<String, Object> asMap() {
        Snapshot snapshot = snapshot();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("analyses", snapshot.analyses());
        metrics.put("deterministicDiagnoses", snapshot.deterministicDiagnoses());
        metrics.put("unknownDiagnoses", snapshot.unknownDiagnoses());
        metrics.put("noFailure", snapshot.noFailure());
        metrics.put("llmUsed", snapshot.llmUsed());
        metrics.put("analysisErrors", snapshot.analysisErrors());
        metrics.put("averageLatencyMs", snapshot.averageLatencyMs());
        metrics.put("maxLatencyMs", snapshot.maxLatencyMs());
        return metrics;
    }

    /**
     * Prometheus text exposition format. Values are aggregate process-local metrics only.
     */
    public String prometheusText() {
        Snapshot s = snapshot();
        StringBuilder out = new StringBuilder();
        counter(out, "log_doctor_analyses_total", "Completed analysis requests", s.analyses());
        counter(out, "log_doctor_deterministic_diagnoses_total", "Deterministic diagnosis outcomes", s.deterministicDiagnoses());
        counter(out, "log_doctor_unknown_diagnoses_total", "Unknown diagnosis outcomes", s.unknownDiagnoses());
        counter(out, "log_doctor_no_failure_total", "Analyses where no failure was detected", s.noFailure());
        counter(out, "log_doctor_llm_used_total", "Analyses that used the configured local LLM", s.llmUsed());
        counter(out, "log_doctor_analysis_errors_total", "Unexpected analysis errors", s.analysisErrors());
        gauge(out, "log_doctor_analysis_latency_average_ms", "Average completed analysis latency in milliseconds", s.averageLatencyMs());
        gauge(out, "log_doctor_analysis_latency_max_ms", "Maximum completed analysis latency in milliseconds", s.maxLatencyMs());
        return out.toString();
    }

    private static void counter(StringBuilder out, String name, String help, long value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" counter\n")
                .append(name).append(' ').append(value).append('\n');
    }

    private static void gauge(StringBuilder out, String name, String help, double value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" gauge\n")
                .append(name).append(' ').append(String.format(Locale.ROOT, "%.2f", value)).append('\n');
    }

    private static double nanosToMillis(long nanos) {
        return Math.round((nanos / 1_000_000.0) * 100.0) / 100.0;
    }

    public record Snapshot(
            long analyses,
            long deterministicDiagnoses,
            long unknownDiagnoses,
            long noFailure,
            long llmUsed,
            long analysisErrors,
            double averageLatencyMs,
            double maxLatencyMs) {}
}
