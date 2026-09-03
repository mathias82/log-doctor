package io.github.mathias82.logdoctor.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeMetricsTest {
    @Test
    void aggregatesOperationalMetricsWithoutPayloadData() {
        RuntimeMetrics metrics = populatedMetrics();
        RuntimeMetrics.Snapshot snapshot = metrics.snapshot();
        assertThat(snapshot.analyses()).isEqualTo(3);
        assertThat(snapshot.incidents()).isEqualTo(4);
        assertThat(snapshot.deterministicDiagnoses()).isEqualTo(1);
        assertThat(snapshot.unknownDiagnoses()).isEqualTo(1);
        assertThat(snapshot.noFailure()).isEqualTo(1);
        assertThat(snapshot.llmUsed()).isEqualTo(1);
        assertThat(snapshot.analysisErrors()).isEqualTo(1);
        assertThat(snapshot.ruleProviderFailures()).isEqualTo(1);
        assertThat(snapshot.averageLatencyMs()).isEqualTo(20.0);
        assertThat(snapshot.maxLatencyMs()).isEqualTo(30.0);
        assertThat(metrics.asMap().keySet()).containsExactly("analyses", "incidents", "deterministicDiagnoses",
                "unknownDiagnoses", "noFailure", "llmUsed", "analysisErrors", "ruleProviderFailures",
                "averageLatencyMs", "maxLatencyMs");
    }

    @Test
    void exposesPrometheusHistogramAndCountersWithoutPayloadData() {
        String prometheus = populatedMetrics().prometheusText();
        assertThat(prometheus)
                .contains("log_doctor_analyses_total 3")
                .contains("log_doctor_incidents_total 4")
                .contains("log_doctor_rule_provider_failures_total 1")
                .contains("# TYPE log_doctor_analysis_latency_milliseconds histogram")
                .contains("log_doctor_analysis_latency_milliseconds_bucket{le=\"10\"} 1")
                .contains("log_doctor_analysis_latency_milliseconds_bucket{le=\"25\"} 2")
                .contains("log_doctor_analysis_latency_milliseconds_bucket{le=\"+Inf\"} 3")
                .contains("log_doctor_analysis_latency_milliseconds_sum 60.00")
                .contains("log_doctor_analysis_latency_milliseconds_count 3")
                .doesNotContain("evidence", "prompt", "rootCause");
    }

    private static RuntimeMetrics populatedMetrics() {
        RuntimeMetrics metrics = new RuntimeMetrics();
        metrics.record("DIAGNOSED", false, 10_000_000L, 3);
        metrics.record("UNKNOWN", true, 30_000_000L, 1);
        metrics.record("NO_FAILURE", false, 20_000_000L, 0);
        metrics.recordError();
        metrics.recordRuleProviderFailure();
        return metrics;
    }
}
