package io.github.mathias82.logdoctor.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceBenchmarkComparatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PerformanceBenchmarkComparator.Thresholds THRESHOLDS =
            new PerformanceBenchmarkComparator.Thresholds(25.0, 20.0, 50.0, 10.0);

    private final PerformanceBenchmarkComparator comparator = new PerformanceBenchmarkComparator();

    @Test
    void reportsRelativeRegressionsWithoutTurningWarningsIntoCorrectnessFailures() throws Exception {
        JsonNode baseline = benchmark(runtime(21, 2, 1_073_741_824L), scenario(
                "medium", 100, 120, 140, 20, 1_000));
        JsonNode current = benchmark(runtime(21, 2, 1_073_741_824L), scenario(
                "medium", 130, 156, 168, 15, 1_700));

        var report = comparator.compare(baseline, current, THRESHOLDS);

        assertThat(report.status()).isEqualTo("COMPARABLE_WITH_WARNINGS");
        assertThat(report.environmentCompatible()).isTrue();
        assertThat(report.correctnessAssertions()).isEqualTo("PASSED");
        assertThat(report.warnings())
                .extracting(PerformanceBenchmarkComparator.PerformanceWarning::metric)
                .containsExactlyInAnyOrder(
                        "p50LatencyMs", "p95LatencyMs", "throughputMiBPerSecondAtP50",
                        "maxObservedHeapDeltaBytes");
        assertThat(report.warnings())
                .noneMatch(warning -> warning.metric().equals("p99LatencyMs"));
        assertThat(comparator.markdown(report))
                .contains("Correctness failures remain hard test failures")
                .contains("medium / p50LatencyMs")
                .contains("+30.00% ⚠");
    }

    @Test
    void refusesMetricComparisonWhenRuntimeEnvironmentIsMateriallyDifferent() throws Exception {
        JsonNode baseline = benchmark(runtime(21, 2, 1_073_741_824L), scenario(
                "medium", 100, 120, 140, 20, 1_000));
        JsonNode current = benchmark(runtime(21, 4, 2_147_483_648L), scenario(
                "medium", 50, 60, 70, 40, 500));

        var report = comparator.compare(baseline, current, THRESHOLDS);

        assertThat(report.status()).isEqualTo("INCOMPATIBLE_ENVIRONMENT");
        assertThat(report.environmentCompatible()).isFalse();
        assertThat(report.runtimeDifferences())
                .filteredOn(PerformanceBenchmarkComparator.RuntimeDifference::material)
                .extracting(PerformanceBenchmarkComparator.RuntimeDifference::field)
                .contains("availableProcessors", "maxHeapBytes");
        assertThat(report.warnings()).isEmpty();
        assertThat(report.scenarios()).allMatch(scenario -> scenario.status().equals("NOT_COMPARED"));
    }

    @Test
    void permitsAdditiveScenariosWithoutSnapshotChurn() throws Exception {
        JsonNode baseline = benchmark(runtime(21, 2, 1_073_741_824L), scenario(
                "small", 10, 12, 14, 30, 100));
        JsonNode current = benchmark(runtime(21, 2, 1_073_741_824L),
                scenario("small", 10, 12, 14, 30, 100),
                scenario("new-large", 100, 120, 140, 10, 2_000));

        var report = comparator.compare(baseline, current, THRESHOLDS);

        assertThat(report.status()).isEqualTo("COMPARABLE_NO_WARNINGS");
        assertThat(report.currentOnlyScenarios()).containsExactly("new-large");
        assertThat(report.scenarios())
                .filteredOn(scenario -> scenario.scenario().equals("new-large"))
                .singleElement()
                .extracting(PerformanceBenchmarkComparator.ScenarioComparison::status)
                .isEqualTo("CURRENT_ONLY");
    }

    private static JsonNode benchmark(String runtime, String... scenarios) throws Exception {
        return JSON.readTree("""
                {
                  "benchmarkType": "synthetic-deterministic-regression",
                  "correctnessAssertions": "PASSED",
                  "runtime": %s,
                  "scenarios": [%s]
                }
                """.formatted(runtime, String.join(",", scenarios)));
    }

    private static String runtime(int javaFeature, int processors, long maxHeapBytes) {
        return """
                {
                  "javaVersion": "21.0.8",
                  "javaFeatureVersion": %d,
                  "vmName": "OpenJDK 64-Bit Server VM",
                  "osName": "Linux",
                  "osArch": "amd64",
                  "availableProcessors": %d,
                  "maxHeapBytes": %d
                }
                """.formatted(javaFeature, processors, maxHeapBytes);
    }

    private static String scenario(
            String name,
            double p50,
            double p95,
            double p99,
            double throughput,
            long heapDelta
    ) {
        return """
                {
                  "name": "%s",
                  "p50LatencyMs": %.2f,
                  "p95LatencyMs": %.2f,
                  "p99LatencyMs": %.2f,
                  "throughputMiBPerSecondAtP50": %.2f,
                  "maxObservedHeapDeltaBytes": %d
                }
                """.formatted(name, p50, p95, p99, throughput, heapDelta);
    }
}
