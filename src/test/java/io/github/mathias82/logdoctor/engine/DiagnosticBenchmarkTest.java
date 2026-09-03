package io.github.mathias82.logdoctor.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticBenchmarkTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double MIN_PRECISION = 0.95;
    private static final double MIN_RECALL = 0.90;
    private static final double MAX_FALSE_POSITIVE_RATE = 0.05;
    private static final double MIN_EXACT_RULE_ACCURACY = 0.90;
    private static final double MIN_CATEGORY_PRECISION = 0.90;
    private static final double MIN_CATEGORY_RECALL = 0.85;
    private static final double MAX_CATEGORY_FALSE_POSITIVE_RATE = 0.10;
    private static final double MIN_CATEGORY_EXACT_RULE_ACCURACY = 0.85;
    private static final int MIN_CORPUS_SIZE = 100;
    private static final int MIN_CASES_PER_CATEGORY = 25;

    @Test
    void benchmarkCuratedCorpusAndPublishMachineReadableMetrics() throws Exception {
        List<BenchmarkCase> corpus = loadCorpus();
        IncidentDetector detector = new IncidentDetector();
        MutableStats overall = new MutableStats();
        Map<String, MutableStats> byCategory = new LinkedHashMap<>();

        for (BenchmarkCase benchmarkCase : corpus) {
            var detection = detector.detectDetailed(new RuleContext(List.of(), null, benchmarkCase.log()));
            boolean matched = detection.isPresent();
            String actualRule = matched ? detection.orElseThrow().rule() : null;

            overall.record(benchmarkCase, matched, actualRule);
            byCategory.computeIfAbsent(benchmarkCase.category(), ignored -> new MutableStats())
                    .record(benchmarkCase, matched, actualRule);
        }

        Map<String, Object> report = overall.toReport();
        Map<String, Object> categoryReports = new LinkedHashMap<>();
        byCategory.forEach((category, stats) -> categoryReports.put(category, stats.toReport()));
        report.put("categories", categoryReports);
        report.put("minimumPrecision", MIN_PRECISION);
        report.put("minimumRecall", MIN_RECALL);
        report.put("maximumFalsePositiveRate", MAX_FALSE_POSITIVE_RATE);
        report.put("minimumExactRuleAccuracy", MIN_EXACT_RULE_ACCURACY);
        report.put("minimumCategoryPrecision", MIN_CATEGORY_PRECISION);
        report.put("minimumCategoryRecall", MIN_CATEGORY_RECALL);
        report.put("maximumCategoryFalsePositiveRate", MAX_CATEGORY_FALSE_POSITIVE_RATE);
        report.put("minimumCategoryExactRuleAccuracy", MIN_CATEGORY_EXACT_RULE_ACCURACY);

        Path output = Path.of("target", "diagnostic-benchmark.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertThat(corpus.size()).as("labelled corpus size").isGreaterThanOrEqualTo(MIN_CORPUS_SIZE);
        assertThat(byCategory.keySet()).containsExactlyInAnyOrder("JVM", "SPRING", "KAFKA", "DB");
        byCategory.forEach((category, stats) -> {
            assertThat(stats.total()).as(category + " corpus size").isGreaterThanOrEqualTo(MIN_CASES_PER_CATEGORY);
            assertThat(stats.precision()).as(category + " precision").isGreaterThanOrEqualTo(MIN_CATEGORY_PRECISION);
            assertThat(stats.recall()).as(category + " recall").isGreaterThanOrEqualTo(MIN_CATEGORY_RECALL);
            assertThat(stats.falsePositiveRate()).as(category + " false-positive rate").isLessThanOrEqualTo(MAX_CATEGORY_FALSE_POSITIVE_RATE);
            assertThat(stats.exactRuleAccuracy()).as(category + " exact rule accuracy").isGreaterThanOrEqualTo(MIN_CATEGORY_EXACT_RULE_ACCURACY);
        });

        assertThat(overall.precision()).as("diagnostic precision").isGreaterThanOrEqualTo(MIN_PRECISION);
        assertThat(overall.recall()).as("diagnostic recall").isGreaterThanOrEqualTo(MIN_RECALL);
        assertThat(overall.falsePositiveRate()).as("false-positive rate").isLessThanOrEqualTo(MAX_FALSE_POSITIVE_RATE);
        assertThat(overall.exactRuleAccuracy()).as("exact specialized-rule accuracy").isGreaterThanOrEqualTo(MIN_EXACT_RULE_ACCURACY);
    }

    private static List<BenchmarkCase> loadCorpus() throws Exception {
        try (InputStream input = DiagnosticBenchmarkTest.class.getResourceAsStream("/diagnostic-benchmark/corpus.json")) {
            if (input == null) {
                throw new IllegalStateException("Diagnostic benchmark corpus is missing");
            }
            return JSON.readValue(input, new TypeReference<>() {});
        }
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static double rounded(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record BenchmarkCase(String name, String category, boolean positive, String expectedRule, String log) {}

    private static final class MutableStats {
        private int truePositive;
        private int falsePositive;
        private int trueNegative;
        private int falseNegative;
        private int exactRuleMatches;
        private int positives;

        void record(BenchmarkCase benchmarkCase, boolean matched, String actualRule) {
            if (benchmarkCase.positive()) {
                positives++;
                if (matched) {
                    truePositive++;
                    if (benchmarkCase.expectedRule() != null && benchmarkCase.expectedRule().equals(actualRule)) {
                        exactRuleMatches++;
                    }
                } else {
                    falseNegative++;
                }
            } else if (matched) {
                falsePositive++;
            } else {
                trueNegative++;
            }
        }

        int total() {
            return truePositive + falsePositive + trueNegative + falseNegative;
        }

        double precision() {
            return ratio(truePositive, truePositive + falsePositive);
        }

        double recall() {
            return ratio(truePositive, truePositive + falseNegative);
        }

        double falsePositiveRate() {
            return ratio(falsePositive, falsePositive + trueNegative);
        }

        double exactRuleAccuracy() {
            return ratio(exactRuleMatches, positives);
        }

        Map<String, Object> toReport() {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("corpusSize", total());
            report.put("positives", positives);
            report.put("negatives", total() - positives);
            report.put("truePositive", truePositive);
            report.put("falsePositive", falsePositive);
            report.put("trueNegative", trueNegative);
            report.put("falseNegative", falseNegative);
            report.put("precision", rounded(precision()));
            report.put("recall", rounded(recall()));
            report.put("falsePositiveRate", rounded(falsePositiveRate()));
            report.put("exactRuleAccuracy", rounded(exactRuleAccuracy()));
            return report;
        }
    }
}
