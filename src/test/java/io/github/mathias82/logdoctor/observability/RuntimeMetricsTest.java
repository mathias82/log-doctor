package io.github.mathias82.logdoctor.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeMetricsTest {

    @Test
    void aggregatesOperationalMetricsWithoutPayloadData() {
        RuntimeMetrics metrics = new RuntimeMetrics();

        metrics.record("DIAGNOSED", false, 10_000_000L);
        metrics.record("UNKNOWN", true, 30_000_000L);
        metrics.record("NO_FAILURE", false, 20_000_000L);
        metrics.recordError();

        RuntimeMetrics.Snapshot snapshot = metrics.snapshot();
        assertThat(snapshot.analyses()).isEqualTo(3);
        assertThat(snapshot.deterministicDiagnoses()).isEqualTo(1);
        assertThat(snapshot.unknownDiagnoses()).isEqualTo(1);
        assertThat(snapshot.noFailure()).isEqualTo(1);
        assertThat(snapshot.llmUsed()).isEqualTo(1);
        assertThat(snapshot.analysisErrors()).isEqualTo(1);
        assertThat(snapshot.averageLatencyMs()).isEqualTo(20.0);
        assertThat(snapshot.maxLatencyMs()).isEqualTo(30.0);
        assertThat(metrics.asMap().keySet()).containsExactly(
                "analyses",
                "deterministicDiagnoses",
                "unknownDiagnoses",
                "noFailure",
                "llmUsed",
                "analysisErrors",
                "averageLatencyMs",
                "maxLatencyMs");
    }
}
