package io.github.mathias82.logdoctor.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class IndependentDiagnosticEvaluationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> CATEGORIES = List.of("JVM", "SPRING", "KAFKA", "DB");
    private static final List<String> CASE_KINDS = List.of(
            "POSITIVE", "HARD_NEGATIVE", "AMBIGUOUS", "CROSS_SUBSYSTEM_LOOKALIKE");
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(password\\s*[=:]|authorization\\s*:\\s*bearer|api[_-]?key\\s*[=:]|secret\\s*[=:]|AKIA[0-9A-Z]{16})");

    @Test
    void publishIndependentPublicationSafeEvaluationEvidence() throws Exception {
        EvaluationDataset dataset = loadDataset();
        validateDataset(dataset);

        Map<String, Object> first = evaluate(dataset);
        Map<String, Object> second = evaluate(dataset);

        assertThat(first).as("evaluation output must be deterministic").isEqualTo(second);

        Path output = Path.of("target", "diagnostic-evaluation.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), first);

        assertThat(output).exists();
        assertThat(first.get("corpusSize")).isEqualTo(dataset.cases().size());
        Map<?, ?> reportedCategories = (Map<?, ?>) first.get("categories");
        assertThat(reportedCategories).hasSize(CATEGORIES.size());
        assertThat(reportedCategories.keySet().containsAll(CATEGORIES)).isTrue();
    }

    private static EvaluationDataset loadDataset() throws Exception {
        try (InputStream input = IndependentDiagnosticEvaluationTest.class
                .getResourceAsStream("/diagnostic-evaluation/corpus.json")) {
            if (input == null) {
                throw new IllegalStateException("Independent diagnostic evaluation corpus is missing");
            }
            return JSON.readValue(input, EvaluationDataset.class);
        }
    }

    private static void validateDataset(EvaluationDataset dataset) throws Exception {
        assertThat(dataset.datasetId()).isEqualTo("log-doctor-independent-evaluation");
        assertThat(dataset.schemaVersion()).isEqualTo(1);
        assertThat(dataset.datasetVersion()).isNotBlank();
        assertThat(dataset.provenance()).isNotNull();
        assertThat(dataset.provenance().rawProductionLogsUsed()).isFalse();
        assertThat(dataset.provenance().labelsDerivedFromDetectorOutput()).isFalse();
        assertThat(dataset.provenance().origin()).contains("no user or production logs");
        assertThat(dataset.provenance().labelingMethod()).contains("before evaluation");
        assertThat(dataset.provenance().limitations()).isNotEmpty();
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(40);

        Set<String> ids = new LinkedHashSet<>();
        Map<String, Set<String>> kindsByCategory = new LinkedHashMap<>();
        CATEGORIES.forEach(category -> kindsByCategory.put(category, new LinkedHashSet<>()));
        Set<String> regressionLogs = loadRegressionLogs();

        for (EvaluationCase evaluationCase : dataset.cases()) {
            assertThat(ids.add(evaluationCase.id())).as("unique case id %s", evaluationCase.id()).isTrue();
            assertThat(evaluationCase.category()).isIn(CATEGORIES);
            assertThat(evaluationCase.kind()).isIn(CASE_KINDS);
            assertThat(evaluationCase.source()).isIn("SYNTHETIC", "SANITIZED");
            assertThat(evaluationCase.labelRationale()).isNotBlank();
            assertThat(evaluationCase.log()).isNotBlank();
            assertThat(SENSITIVE_VALUE.matcher(evaluationCase.log()).find())
                    .as("publication-safe case %s", evaluationCase.id())
                    .isFalse();
            assertThat(regressionLogs).as("evaluation cases must be separate from regression fixtures")
                    .doesNotContain(evaluationCase.log());

            if (evaluationCase.expectedMatch()) {
                assertThat(evaluationCase.kind()).isEqualTo("POSITIVE");
                assertThat(evaluationCase.expectedRule()).isNotBlank();
            } else {
                assertThat(evaluationCase.kind()).isNotEqualTo("POSITIVE");
                assertThat(evaluationCase.expectedRule()).isNull();
            }
            kindsByCategory.get(evaluationCase.category()).add(evaluationCase.kind());
        }

        for (String category : CATEGORIES) {
            long categoryCases = dataset.cases().stream()
                    .filter(item -> category.equals(item.category()))
                    .count();
            assertThat(categoryCases).as(category + " evaluation case count").isGreaterThanOrEqualTo(10);
            assertThat(kindsByCategory.get(category)).as(category + " case kinds")
                    .containsExactlyInAnyOrderElementsOf(CASE_KINDS);
        }
    }

    private static Set<String> loadRegressionLogs() throws Exception {
        try (InputStream input = IndependentDiagnosticEvaluationTest.class
                .getResourceAsStream("/diagnostic-benchmark/corpus.json")) {
            if (input == null) {
                throw new IllegalStateException("Regression corpus is missing");
            }
            Set<String> logs = new LinkedHashSet<>();
            for (JsonNode item : JSON.readTree(input)) {
                logs.add(item.path("log").asText());
            }
            return logs;
        }
    }

    private static Map<String, Object> evaluate(EvaluationDataset dataset) {
        IncidentDetector detector = new IncidentDetector();
        MutableStats overall = new MutableStats();
        Map<String, MutableStats> categoryStats = new LinkedHashMap<>();
        CATEGORIES.forEach(category -> categoryStats.put(category, new MutableStats()));
        Map<String, Integer> kindCounts = new LinkedHashMap<>();
        CASE_KINDS.forEach(kind -> kindCounts.put(kind, 0));
        List<Map<String, Object>> mismatches = new ArrayList<>();

        for (EvaluationCase evaluationCase : dataset.cases()) {
            var detection = detector.detectDetailed(new RuleContext(List.of(), null, evaluationCase.log()));
            boolean matched = detection.isPresent();
            String actualRule = matched ? detection.orElseThrow().rule() : null;

            overall.record(evaluationCase, matched, actualRule);
            categoryStats.get(evaluationCase.category()).record(evaluationCase, matched, actualRule);
            kindCounts.merge(evaluationCase.kind(), 1, Integer::sum);

            boolean classificationMismatch = evaluationCase.expectedMatch() != matched;
            boolean ruleMismatch = evaluationCase.expectedMatch()
                    && matched
                    && !evaluationCase.expectedRule().equals(actualRule);
            if (classificationMismatch || ruleMismatch) {
                Map<String, Object> mismatch = new LinkedHashMap<>();
                mismatch.put("id", evaluationCase.id());
                mismatch.put("category", evaluationCase.category());
                mismatch.put("kind", evaluationCase.kind());
                mismatch.put("expectedMatch", evaluationCase.expectedMatch());
                mismatch.put("actualMatch", matched);
                mismatch.put("expectedRule", evaluationCase.expectedRule());
                mismatch.put("actualRule", actualRule);
                mismatch.put("labelRationale", evaluationCase.labelRationale());
                mismatches.add(mismatch);
            }
        }

        Map<String, Object> categories = new LinkedHashMap<>();
        categoryStats.forEach((category, stats) -> categories.put(category, stats.toReport()));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("evaluationType", "independently-labelled-publication-safe");
        report.put("datasetId", dataset.datasetId());
        report.put("schemaVersion", dataset.schemaVersion());
        report.put("datasetVersion", dataset.datasetVersion());
        report.put("provenance", dataset.provenance());
        report.put("corpusSize", dataset.cases().size());
        report.put("caseKinds", kindCounts);
        report.put("metrics", overall.toReport());
        report.put("categories", categories);
        report.put("mismatches", mismatches);
        report.put("interpretation", List.of(
                "Evaluation evidence is reported separately from the curated regression quality gates.",
                "No evaluation metric is a release gate in this initial version.",
                "Results do not claim production-wide statistical accuracy."));
        return report;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static double rounded(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record EvaluationDataset(
            String datasetId,
            int schemaVersion,
            String datasetVersion,
            Provenance provenance,
            List<EvaluationCase> cases
    ) {}

    private record Provenance(
            String origin,
            String labelingMethod,
            boolean rawProductionLogsUsed,
            boolean labelsDerivedFromDetectorOutput,
            List<String> limitations
    ) {}

    private record EvaluationCase(
            String id,
            String category,
            String kind,
            String source,
            boolean expectedMatch,
            String expectedRule,
            String labelRationale,
            String log
    ) {}

    private static final class MutableStats {
        private int truePositive;
        private int falsePositive;
        private int trueNegative;
        private int falseNegative;
        private int exactRuleMatches;
        private int positives;

        void record(EvaluationCase evaluationCase, boolean matched, String actualRule) {
            if (evaluationCase.expectedMatch()) {
                positives++;
                if (matched) {
                    truePositive++;
                    if (evaluationCase.expectedRule().equals(actualRule)) {
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

        Map<String, Object> toReport() {
            int total = truePositive + falsePositive + trueNegative + falseNegative;
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("corpusSize", total);
            report.put("positives", positives);
            report.put("negatives", total - positives);
            report.put("truePositive", truePositive);
            report.put("falsePositive", falsePositive);
            report.put("trueNegative", trueNegative);
            report.put("falseNegative", falseNegative);
            report.put("precision", rounded(ratio(truePositive, truePositive + falsePositive)));
            report.put("recall", rounded(ratio(truePositive, truePositive + falseNegative)));
            report.put("falsePositiveRate", rounded(ratio(falsePositive, falsePositive + trueNegative)));
            report.put("exactRuleAccuracy", rounded(ratio(exactRuleMatches, positives)));
            return report;
        }
    }
}
