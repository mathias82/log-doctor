package io.github.mathias82.logdoctor.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Privacy-safe, process-local operational metrics for Log Doctor. */
public final class RuntimeMetrics {
    private static final double[] LATENCY_BUCKETS_MS = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};

    private final LongAdder analyses = new LongAdder();
    private final LongAdder incidents = new LongAdder();
    private final LongAdder deterministicDiagnoses = new LongAdder();
    private final LongAdder unknownDiagnoses = new LongAdder();
    private final LongAdder noFailure = new LongAdder();
    private final LongAdder llmUsed = new LongAdder();
    private final LongAdder analysisErrors = new LongAdder();
    private final LongAdder ruleProviderFailures = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong maxLatencyNanos = new AtomicLong();
    private final LongAdder[] latencyBuckets = new LongAdder[LATENCY_BUCKETS_MS.length];

    public RuntimeMetrics() {
        for (int i = 0; i < latencyBuckets.length; i++) latencyBuckets[i] = new LongAdder();
    }

    public void record(String status, boolean usedLlm, long elapsedNanos) {
        record(status, usedLlm, elapsedNanos, "NO_FAILURE".equals(status) ? 0 : 1);
    }

    public void record(String status, boolean usedLlm, long elapsedNanos, int incidentCount) {
        analyses.increment();
        incidents.add(Math.max(0, incidentCount));
        long safeElapsed = Math.max(0L, elapsedNanos);
        totalLatencyNanos.add(safeElapsed);
        maxLatencyNanos.accumulateAndGet(safeElapsed, Math::max);
        double elapsedMs = safeElapsed / 1_000_000.0;
        for (int i = 0; i < LATENCY_BUCKETS_MS.length; i++) {
            if (elapsedMs <= LATENCY_BUCKETS_MS[i]) latencyBuckets[i].increment();
        }
        if ("DIAGNOSED".equals(status)) deterministicDiagnoses.increment();
        else if ("UNKNOWN".equals(status)) unknownDiagnoses.increment();
        else if ("NO_FAILURE".equals(status)) noFailure.increment();
        if (usedLlm) llmUsed.increment();
    }

    public void recordError() { analysisErrors.increment(); }
    public void recordRuleProviderFailure() { ruleProviderFailures.increment(); }

    public Snapshot snapshot() {
        long count = analyses.sum();
        long total = totalLatencyNanos.sum();
        return new Snapshot(count, incidents.sum(), deterministicDiagnoses.sum(), unknownDiagnoses.sum(), noFailure.sum(),
                llmUsed.sum(), analysisErrors.sum(), ruleProviderFailures.sum(),
                nanosToMillis(count == 0 ? 0 : total / count), nanosToMillis(maxLatencyNanos.get()));
    }

    public Map<String, Object> asMap() {
        Snapshot s = snapshot();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("analyses", s.analyses());
        metrics.put("incidents", s.incidents());
        metrics.put("deterministicDiagnoses", s.deterministicDiagnoses());
        metrics.put("unknownDiagnoses", s.unknownDiagnoses());
        metrics.put("noFailure", s.noFailure());
        metrics.put("llmUsed", s.llmUsed());
        metrics.put("analysisErrors", s.analysisErrors());
        metrics.put("ruleProviderFailures", s.ruleProviderFailures());
        metrics.put("averageLatencyMs", s.averageLatencyMs());
        metrics.put("maxLatencyMs", s.maxLatencyMs());
        return metrics;
    }

    public String prometheusText() {
        Snapshot s = snapshot();
        StringBuilder out = new StringBuilder();
        counter(out, "log_doctor_analyses_total", "Completed analysis requests", s.analyses());
        counter(out, "log_doctor_incidents_total", "Incidents returned across completed analysis requests", s.incidents());
        counter(out, "log_doctor_deterministic_diagnoses_total", "Deterministic diagnosis outcomes", s.deterministicDiagnoses());
        counter(out, "log_doctor_unknown_diagnoses_total", "Unknown diagnosis outcomes", s.unknownDiagnoses());
        counter(out, "log_doctor_no_failure_total", "Analyses where no failure was detected", s.noFailure());
        counter(out, "log_doctor_llm_used_total", "Analyses that used the configured local LLM", s.llmUsed());
        counter(out, "log_doctor_analysis_errors_total", "Unexpected analysis errors", s.analysisErrors());
        counter(out, "log_doctor_rule_provider_failures_total", "Fail-soft custom rule provider or rule failures", s.ruleProviderFailures());
        histogram(out);
        gauge(out, "log_doctor_analysis_latency_average_ms", "Average completed analysis latency in milliseconds", s.averageLatencyMs());
        gauge(out, "log_doctor_analysis_latency_max_ms", "Maximum completed analysis latency in milliseconds", s.maxLatencyMs());
        return out.toString();
    }

    private void histogram(StringBuilder out) {
        out.append("# HELP log_doctor_analysis_latency_milliseconds Completed analysis request latency\n")
                .append("# TYPE log_doctor_analysis_latency_milliseconds histogram\n");
        for (int i = 0; i < LATENCY_BUCKETS_MS.length; i++) {
            out.append("log_doctor_analysis_latency_milliseconds_bucket{le=\"")
                    .append(String.format(Locale.ROOT, "%.0f", LATENCY_BUCKETS_MS[i])).append("\"} ")
                    .append(latencyBuckets[i].sum()).append('\n');
        }
        long count = analyses.sum();
        out.append("log_doctor_analysis_latency_milliseconds_bucket{le=\"+Inf\"} ").append(count).append('\n')
                .append("log_doctor_analysis_latency_milliseconds_sum ")
                .append(String.format(Locale.ROOT, "%.2f", totalLatencyNanos.sum() / 1_000_000.0)).append('\n')
                .append("log_doctor_analysis_latency_milliseconds_count ").append(count).append('\n');
    }

    private static void counter(StringBuilder out, String name, String help, long value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" counter\n").append(name).append(' ').append(value).append('\n');
    }

    private static void gauge(StringBuilder out, String name, String help, double value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" gauge\n").append(name).append(' ')
                .append(String.format(Locale.ROOT, "%.2f", value)).append('\n');
    }

    private static double nanosToMillis(long nanos) { return Math.round((nanos / 1_000_000.0) * 100.0) / 100.0; }

    public record Snapshot(long analyses, long incidents, long deterministicDiagnoses, long unknownDiagnoses,
                           long noFailure, long llmUsed, long analysisErrors, long ruleProviderFailures,
                           double averageLatencyMs, double maxLatencyMs) {}
}
