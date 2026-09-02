package io.github.mathias82.logdoctor.observability;

import java.util.LinkedHashMap;
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
