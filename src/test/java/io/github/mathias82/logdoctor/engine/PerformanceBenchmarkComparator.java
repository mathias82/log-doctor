package io.github.mathias82.logdoctor.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PerformanceBenchmarkComparator {
    private static final List<MetricDefinition> METRICS = List.of(
            new MetricDefinition("p50LatencyMs", Direction.LOWER_IS_BETTER, ThresholdKind.LATENCY),
            new MetricDefinition("p95LatencyMs", Direction.LOWER_IS_BETTER, ThresholdKind.LATENCY),
            new MetricDefinition("p99LatencyMs", Direction.LOWER_IS_BETTER, ThresholdKind.LATENCY),
            new MetricDefinition("throughputMiBPerSecondAtP50", Direction.HIGHER_IS_BETTER, ThresholdKind.THROUGHPUT),
            new MetricDefinition("maxObservedHeapDeltaBytes", Direction.LOWER_IS_BETTER, ThresholdKind.HEAP));
    private static final List<String> EXACT_RUNTIME_FIELDS = List.of(
            "javaFeatureVersion", "vmName", "osName", "osArch", "availableProcessors");

    ComparisonReport compare(JsonNode baseline, JsonNode current, Thresholds thresholds) {
        requireBenchmark(baseline, "baseline");
        requireBenchmark(current, "current");
        thresholds.validate();

        List<RuntimeDifference> runtimeDifferences = runtimeDifferences(
                baseline.path("runtime"), current.path("runtime"), thresholds.runtimeHeapTolerancePercent());
        boolean environmentCompatible = runtimeDifferences.stream().noneMatch(RuntimeDifference::material);
        Map<String, JsonNode> baselineScenarios = scenariosByName(baseline);
        Map<String, JsonNode> currentScenarios = scenariosByName(current);
        List<String> baselineOnly = baselineScenarios.keySet().stream()
                .filter(name -> !currentScenarios.containsKey(name))
                .toList();
        List<String> currentOnly = currentScenarios.keySet().stream()
                .filter(name -> !baselineScenarios.containsKey(name))
                .toList();
        List<ScenarioComparison> scenarioComparisons = new ArrayList<>();
        List<PerformanceWarning> warnings = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : currentScenarios.entrySet()) {
            JsonNode baselineScenario = baselineScenarios.get(entry.getKey());
            if (baselineScenario == null) {
                scenarioComparisons.add(new ScenarioComparison(entry.getKey(), "CURRENT_ONLY", List.of()));
                continue;
            }
            if (!environmentCompatible) {
                scenarioComparisons.add(new ScenarioComparison(entry.getKey(), "NOT_COMPARED", List.of()));
                continue;
            }

            List<MetricChange> changes = new ArrayList<>();
            for (MetricDefinition metric : METRICS) {
                double baselineValue = requiredNumber(baselineScenario, metric.name(), entry.getKey(), "baseline");
                double currentValue = requiredNumber(entry.getValue(), metric.name(), entry.getKey(), "current");
                double threshold = metric.threshold(thresholds);
                Double relativeChange = relativeChangePercent(baselineValue, currentValue);
                Double regression = regressionPercent(baselineValue, currentValue, metric.direction());
                boolean warning = regression != null && regression > threshold;
                MetricChange change = new MetricChange(
                        metric.name(), baselineValue, currentValue, relativeChange, regression,
                        threshold, metric.direction().name(), warning);
                changes.add(change);
                if (warning) {
                    warnings.add(new PerformanceWarning(entry.getKey(), metric.name(), regression, threshold));
                }
            }
            scenarioComparisons.add(new ScenarioComparison(entry.getKey(), "COMPARED", changes));
        }

        String status;
        if (!environmentCompatible) {
            status = "INCOMPATIBLE_ENVIRONMENT";
        } else if (!warnings.isEmpty()) {
            status = "COMPARABLE_WITH_WARNINGS";
        } else {
            status = "COMPARABLE_NO_WARNINGS";
        }

        return new ComparisonReport(
                1,
                status,
                environmentCompatible,
                current.path("correctnessAssertions").asText("UNKNOWN"),
                thresholds,
                runtimeDifferences,
                scenarioComparisons,
                warnings,
                baselineOnly,
                currentOnly,
                List.of(
                        "Performance warnings are relative signals and do not replace correctness assertions.",
                        "Metrics are not compared when material runtime metadata differs.",
                        "Hosted-runner observations are not production SLAs."));
    }

    String markdown(ComparisonReport report) {
        StringBuilder out = new StringBuilder("# Performance benchmark comparison\n\n")
                .append("- Status: **").append(report.status()).append("**\n")
                .append("- Environment compatible: ").append(report.environmentCompatible()).append('\n')
                .append("- Current benchmark correctness assertions: ")
                .append(report.correctnessAssertions()).append("\n")
                .append(String.format(Locale.ROOT,
                        "- Warning thresholds: latency %.1f%%, throughput %.1f%%, heap %.1f%%\n",
                        report.thresholds().latencyRegressionPercent(),
                        report.thresholds().throughputRegressionPercent(),
                        report.thresholds().heapRegressionPercent()));

        if (!report.runtimeDifferences().isEmpty()) {
            out.append("\n## Runtime metadata differences\n\n")
                    .append("| Field | Baseline | Current | Material |\n")
                    .append("|---|---:|---:|:---:|\n");
            for (RuntimeDifference difference : report.runtimeDifferences()) {
                out.append("| ").append(difference.field())
                        .append(" | ").append(difference.baselineValue())
                        .append(" | ").append(difference.currentValue())
                        .append(" | ").append(difference.material() ? "yes" : "no")
                        .append(" |\n");
            }
        }

        out.append("\n## Performance warnings\n\n");
        if (report.warnings().isEmpty()) {
            out.append(report.environmentCompatible()
                    ? "No configured relative warning threshold was exceeded.\n"
                    : "No warnings were calculated because the runtime environments are materially different.\n");
        } else {
            for (PerformanceWarning warning : report.warnings()) {
                out.append(String.format(Locale.ROOT,
                        "- **%s / %s:** %.2f%% regression (warning above %.2f%%)\n",
                        warning.scenario(), warning.metric(), warning.regressionPercent(), warning.thresholdPercent()));
            }
        }

        out.append("\n## Scenario changes\n\n")
                .append("| Scenario | p50 latency | p95 latency | p99 latency | Throughput | Heap delta |\n")
                .append("|---|---:|---:|---:|---:|---:|\n");
        for (ScenarioComparison scenario : report.scenarios()) {
            Map<String, MetricChange> changes = new LinkedHashMap<>();
            scenario.metrics().forEach(metric -> changes.put(metric.metric(), metric));
            out.append("| ").append(scenario.scenario()).append(" | ")
                    .append(formatChange(changes.get("p50LatencyMs"), scenario.status())).append(" | ")
                    .append(formatChange(changes.get("p95LatencyMs"), scenario.status())).append(" | ")
                    .append(formatChange(changes.get("p99LatencyMs"), scenario.status())).append(" | ")
                    .append(formatChange(changes.get("throughputMiBPerSecondAtP50"), scenario.status())).append(" | ")
                    .append(formatChange(changes.get("maxObservedHeapDeltaBytes"), scenario.status())).append(" |\n");
        }

        if (!report.baselineOnlyScenarios().isEmpty() || !report.currentOnlyScenarios().isEmpty()) {
            out.append("\n## Scenario inventory changes\n\n")
                    .append("- Baseline only: ").append(listOrNone(report.baselineOnlyScenarios())).append('\n')
                    .append("- Current only: ").append(listOrNone(report.currentOnlyScenarios())).append('\n');
        }
        out.append("\n_Correctness failures remain hard test failures. Performance warnings are non-blocking trend signals._\n");
        return out.toString();
    }

    private static List<RuntimeDifference> runtimeDifferences(
            JsonNode baseline,
            JsonNode current,
            double heapTolerancePercent
    ) {
        List<RuntimeDifference> differences = new ArrayList<>();
        for (String field : EXACT_RUNTIME_FIELDS) {
            String baselineValue = value(baseline, field);
            String currentValue = value(current, field);
            if (!baselineValue.equals(currentValue)) {
                differences.add(new RuntimeDifference(field, baselineValue, currentValue, true));
            }
        }

        long baselineHeap = baseline.path("maxHeapBytes").asLong(-1L);
        long currentHeap = current.path("maxHeapBytes").asLong(-1L);
        if (baselineHeap != currentHeap) {
            Double difference = absolutePercentDifference(baselineHeap, currentHeap);
            boolean material = difference == null || difference > heapTolerancePercent;
            differences.add(new RuntimeDifference(
                    "maxHeapBytes", Long.toString(baselineHeap), Long.toString(currentHeap), material));
        }

        String baselineJava = value(baseline, "javaVersion");
        String currentJava = value(current, "javaVersion");
        if (!baselineJava.equals(currentJava)) {
            differences.add(new RuntimeDifference("javaVersion", baselineJava, currentJava, false));
        }
        return differences;
    }

    private static Map<String, JsonNode> scenariosByName(JsonNode report) {
        Map<String, JsonNode> scenarios = new LinkedHashMap<>();
        JsonNode values = report.path("scenarios");
        if (!values.isArray()) {
            throw new IllegalArgumentException("Benchmark report is missing scenarios array");
        }
        for (JsonNode scenario : values) {
            String name = scenario.path("name").asText("");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Benchmark scenario is missing name");
            }
            if (scenarios.put(name, scenario) != null) {
                throw new IllegalArgumentException("Duplicate benchmark scenario: " + name);
            }
        }
        return scenarios;
    }

    private static void requireBenchmark(JsonNode report, String label) {
        if (report == null || !report.isObject()) {
            throw new IllegalArgumentException(label + " benchmark report must be a JSON object");
        }
        if (!report.path("runtime").isObject()) {
            throw new IllegalArgumentException(label + " benchmark report is missing runtime metadata");
        }
    }

    private static double requiredNumber(JsonNode scenario, String metric, String scenarioName, String label) {
        JsonNode value = scenario.get(metric);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(label + " scenario " + scenarioName + " is missing metric " + metric);
        }
        return value.asDouble();
    }

    private static String value(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "<missing>" : value.asText();
    }

    private static Double relativeChangePercent(double baseline, double current) {
        if (baseline == 0.0) {
            return current == 0.0 ? 0.0 : null;
        }
        return round((current - baseline) / Math.abs(baseline) * 100.0);
    }

    private static Double regressionPercent(double baseline, double current, Direction direction) {
        if (baseline == 0.0) {
            return current == 0.0 ? 0.0 : null;
        }
        double regression = direction == Direction.LOWER_IS_BETTER
                ? (current - baseline) / Math.abs(baseline) * 100.0
                : (baseline - current) / Math.abs(baseline) * 100.0;
        return round(Math.max(0.0, regression));
    }

    private static Double absolutePercentDifference(long baseline, long current) {
        if (baseline <= 0 || current <= 0) {
            return null;
        }
        return round(Math.abs(current - baseline) / (double) baseline * 100.0);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String formatChange(MetricChange change, String status) {
        if (change == null || change.relativeChangePercent() == null) {
            return status.equals("COMPARED") ? "n/a" : status.toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return String.format(Locale.ROOT, "%+.2f%%%s", change.relativeChangePercent(), change.warning() ? " ⚠" : "");
    }

    private static String listOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    enum Direction {
        LOWER_IS_BETTER,
        HIGHER_IS_BETTER
    }

    private enum ThresholdKind {
        LATENCY,
        THROUGHPUT,
        HEAP
    }

    private record MetricDefinition(String name, Direction direction, ThresholdKind thresholdKind) {
        double threshold(Thresholds thresholds) {
            return switch (thresholdKind) {
                case LATENCY -> thresholds.latencyRegressionPercent();
                case THROUGHPUT -> thresholds.throughputRegressionPercent();
                case HEAP -> thresholds.heapRegressionPercent();
            };
        }
    }

    record Thresholds(
            double latencyRegressionPercent,
            double throughputRegressionPercent,
            double heapRegressionPercent,
            double runtimeHeapTolerancePercent
    ) {
        void validate() {
            if (latencyRegressionPercent < 0 || throughputRegressionPercent < 0
                    || heapRegressionPercent < 0 || runtimeHeapTolerancePercent < 0) {
                throw new IllegalArgumentException("Performance comparison thresholds must be non-negative");
            }
        }
    }

    record RuntimeDifference(String field, String baselineValue, String currentValue, boolean material) {}

    record MetricChange(
            String metric,
            double baselineValue,
            double currentValue,
            Double relativeChangePercent,
            Double regressionPercent,
            double warningThresholdPercent,
            String direction,
            boolean warning
    ) {}

    record ScenarioComparison(String scenario, String status, List<MetricChange> metrics) {}

    record PerformanceWarning(
            String scenario,
            String metric,
            double regressionPercent,
            double thresholdPercent
    ) {}

    record ComparisonReport(
            int comparisonSchemaVersion,
            String status,
            boolean environmentCompatible,
            String correctnessAssertions,
            Thresholds thresholds,
            List<RuntimeDifference> runtimeDifferences,
            List<ScenarioComparison> scenarios,
            List<PerformanceWarning> warnings,
            List<String> baselineOnlyScenarios,
            List<String> currentOnlyScenarios,
            List<String> notes
    ) {}
}
