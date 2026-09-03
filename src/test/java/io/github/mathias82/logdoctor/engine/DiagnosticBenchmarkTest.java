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

    @Test
    void benchmarkCuratedCorpusAndPublishMachineReadableMetrics() throws Exception {
        List<BenchmarkCase> corpus = loadCorpus();
        IncidentDetector detector = new IncidentDetector();

        int truePositive = 0;
        int falsePositive = 0;
        int trueNegative = 0;
        int falseNegative = 0;
        int exactRuleMatches = 0;
        int positives = 0;

        for (BenchmarkCase benchmarkCase : corpus) {
            var detection = detector.detectDetailed(new RuleContext(List.of(), null, benchmarkCase.log()));
            boolean matched = detection.isPresent();

            if (benchmarkCase.positive()) {
                positives++;
                if (matched) {
                    truePositive++;
                    if (benchmarkCase.expectedRule() != null
                            && benchmarkCase.expectedRule().equals(detection.orElseThrow().rule())) {
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

        double precision = ratio(truePositive, truePositive + falsePositive);
        double recall = ratio(truePositive, truePositive + falseNegative);
        double falsePositiveRate = ratio(falsePositive, falsePositive + trueNegative);
        double exactRuleAccuracy = ratio(exactRuleMatches, positives);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("corpusSize", corpus.size());
        report.put("positives", positives);
        report.put("negatives", corpus.size() - positives);
        report.put("truePositive", truePositive);
        report.put("falsePositive", falsePositive);
        report.put("trueNegative", trueNegative);
        report.put("falseNegative", falseNegative);
        report.put("precision", rounded(precision));
        report.put("recall", rounded(recall));
        report.put("falsePositiveRate", rounded(falsePositiveRate));
        report.put("exactRuleAccuracy", rounded(exactRuleAccuracy));
        report.put("minimumPrecision", MIN_PRECISION);
        report.put("minimumRecall", MIN_RECALL);
        report.put("maximumFalsePositiveRate", MAX_FALSE_POSITIVE_RATE);

        Path output = Path.of("target", "diagnostic-benchmark.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertThat(precision).as("diagnostic precision").isGreaterThanOrEqualTo(MIN_PRECISION);
        assertThat(recall).as("diagnostic recall").isGreaterThanOrEqualTo(MIN_RECALL);
        assertThat(falsePositiveRate).as("false-positive rate").isLessThanOrEqualTo(MAX_FALSE_POSITIVE_RATE);
        assertThat(exactRuleAccuracy).as("exact specialized-rule accuracy").isGreaterThanOrEqualTo(0.90);
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

    private record BenchmarkCase(String name, boolean positive, String expectedRule, String log) {}
}
