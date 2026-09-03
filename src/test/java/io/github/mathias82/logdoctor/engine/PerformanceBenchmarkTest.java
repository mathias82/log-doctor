package io.github.mathias82.logdoctor.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceBenchmarkTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int WARMUP_ITERATIONS = 2;
    private static final int MEASURED_ITERATIONS = 7;

    @Test
    void publishRepeatableBatchPerformanceProfile() throws Exception {
        DiagnosisEngine engine = new DiagnosisEngine(new NoopLlmClient());
        LogBatchAnalyzer analyzer = new LogBatchAnalyzer(engine);

        List<Scenario> scenarios = List.of(
                scenario("small-50-incidents", 50, 8, false),
                scenario("medium-200-incidents", 200, 12, false),
                scenario("max-batch-500-incidents", 500, 16, false),
                scenario("over-cap-750-incidents", 750, 8, true),
                scenarioByTargetBytes("large-log-2mb", 2 * 1024 * 1024)
        );

        List<Map<String, Object>> reports = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                analyzer.analyze(scenario.log());
            }

            List<Long> durations = new ArrayList<>();
            long maxHeapDelta = 0L;
            LogBatchAnalyzer.BatchDiagnosisResult last = null;
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                long beforeHeap = memory.getHeapMemoryUsage().getUsed();
                long started = System.nanoTime();
                last = analyzer.analyze(scenario.log());
                long elapsed = System.nanoTime() - started;
                long afterHeap = memory.getHeapMemoryUsage().getUsed();

                durations.add(elapsed);
                maxHeapDelta = Math.max(maxHeapDelta, Math.max(0L, afterHeap - beforeHeap));
            }

            assertThat(last).isNotNull();
            assertThat(last.detectedFailureBlocks()).isGreaterThan(0);
            assertThat(last.uniqueIncidents()).isGreaterThan(0);
            assertThat(last.truncated()).isEqualTo(scenario.expectedTruncated());
            if (scenario.expectedTruncated()) {
                assertThat(last.detectedFailureBlocks()).isGreaterThan(LogBatchAnalyzer.MAX_INCIDENT_BLOCKS);
            }

            reports.add(reportFor(scenario, durations, maxHeapDelta, last));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("benchmarkType", "synthetic-deterministic-regression");
        report.put("warmupIterations", WARMUP_ITERATIONS);
        report.put("measuredIterations", MEASURED_ITERATIONS);
        report.put("runtime", runtimeMetadata());
        report.put("scenarios", reports);
        report.put("notes", List.of(
                "Latency values are CI/runtime observations, not production SLAs.",
                "Heap delta is an approximate process-local signal and is not a leak detector.",
                "The benchmark intentionally uses a no-op LLM client so results measure deterministic analysis and batch processing.",
                "The over-cap scenario verifies the 500-block safety cap while still recording end-to-end latency."
        ));

        Path output = Path.of("target", "performance-benchmark.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertThat(reports).hasSize(5);
        assertThat(output).exists();
    }

    private static Map<String, Object> reportFor(
            Scenario scenario,
            List<Long> durations,
            long maxHeapDelta,
            LogBatchAnalyzer.BatchDiagnosisResult result) {
        List<Long> sorted = durations.stream().sorted(Comparator.naturalOrder()).toList();
        double p50 = nanosToMillis(percentile(sorted, 0.50));
        double p95 = nanosToMillis(percentile(sorted, 0.95));
        double p99 = nanosToMillis(percentile(sorted, 0.99));
        double averageMs = nanosToMillis((long) durations.stream().mapToLong(Long::longValue).average().orElse(0));
        double throughputMbPerSec = p50 == 0.0 ? 0.0 : round((scenario.bytes() / 1024.0 / 1024.0) / (p50 / 1000.0));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("name", scenario.name());
        report.put("inputBytes", scenario.bytes());
        report.put("inputLines", scenario.lines());
        report.put("detectedFailureBlocks", result.detectedFailureBlocks());
        report.put("processedFailureBlockLimit", LogBatchAnalyzer.MAX_INCIDENT_BLOCKS);
        report.put("uniqueIncidents", result.uniqueIncidents());
        report.put("truncated", result.truncated());
        report.put("averageLatencyMs", averageMs);
        report.put("p50LatencyMs", p50);
        report.put("p95LatencyMs", p95);
        report.put("p99LatencyMs", p99);
        report.put("throughputMiBPerSecondAtP50", throughputMbPerSec);
        report.put("maxObservedHeapDeltaBytes", maxHeapDelta);
        return report;
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double nanosToMillis(long nanos) {
        return round(nanos / 1_000_000.0);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Scenario scenario(String name, int incidents, int stackFrames, boolean expectedTruncated) {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < incidents; i++) {
            log.append("2026-09-03T12:00:").append(String.format("%02d", i % 60))
                    .append("Z ERROR request failed order=").append(i).append('\n');
            log.append("java.lang.NullPointerException: order was null\n");
            for (int frame = 0; frame < stackFrames; frame++) {
                log.append("\tat com.acme.orders.OrderService.step")
                        .append(frame)
                        .append("(OrderService.java:")
                        .append(100 + frame)
                        .append(")\n");
            }
            log.append("2026-09-03T12:01:00Z INFO request completed\n");
        }
        return new Scenario(name, log.toString(), expectedTruncated);
    }

    private static Scenario scenarioByTargetBytes(String name, int targetBytes) {
        String base = scenario(name, 500, 24, false).log();
        StringBuilder log = new StringBuilder(base);
        while (log.length() < targetBytes) {
            log.append("2026-09-03T12:02:00Z INFO heartbeat node=orders-service status=UP detail=")
                    .append("x".repeat(180))
                    .append('\n');
        }
        return new Scenario(name, log.substring(0, Math.min(log.length(), targetBytes)), false);
    }

    private static Map<String, Object> runtimeMetadata() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("vmName", System.getProperty("java.vm.name"));
        runtime.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        runtime.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        return runtime;
    }

    private record Scenario(String name, String log, boolean expectedTruncated) {
        int bytes() {
            return log.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        long lines() {
            return log.lines().count();
        }
    }

    private static final class NoopLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            return null;
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            return null;
        }
    }
}
