package io.github.mathias82.logdoctor.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceBenchmarkComparisonTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void compareConfiguredBenchmarkArtifactsAndPublishReports() throws Exception {
        String baselinePath = System.getProperty("performance.baseline", "");
        String currentPath = System.getProperty("performance.current", "");
        Assumptions.assumeTrue(!baselinePath.isBlank() && !currentPath.isBlank(),
                "Comparison artifacts are supplied by the performance workflow");

        Path baseline = Path.of(baselinePath);
        Path current = Path.of(currentPath);
        assertThat(baseline).exists().isRegularFile();
        assertThat(current).exists().isRegularFile();

        PerformanceBenchmarkComparator.Thresholds thresholds = new PerformanceBenchmarkComparator.Thresholds(
                property("performance.warnLatencyPercent", 25.0),
                property("performance.warnThroughputPercent", 20.0),
                property("performance.warnHeapPercent", 50.0),
                property("performance.runtimeHeapTolerancePercent", 10.0));
        PerformanceBenchmarkComparator comparator = new PerformanceBenchmarkComparator();
        JsonNode baselineReport = JSON.readTree(baseline.toFile());
        JsonNode currentReport = JSON.readTree(current.toFile());
        var comparison = comparator.compare(baselineReport, currentReport, thresholds);

        Path jsonOutput = Path.of("target", "performance-comparison.json");
        Path markdownOutput = Path.of("target", "performance-comparison.md");
        Files.createDirectories(jsonOutput.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(jsonOutput.toFile(), comparison);
        Files.writeString(markdownOutput, comparator.markdown(comparison));

        assertThat(jsonOutput).exists();
        assertThat(markdownOutput).exists();
        assertThat(comparison.correctnessAssertions()).isEqualTo("PASSED");
    }

    private static double property(String name, double defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }
}
