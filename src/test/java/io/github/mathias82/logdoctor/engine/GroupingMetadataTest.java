package io.github.mathias82.logdoctor.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingMetadataTest {

    @Test
    void exposesStackGroupingWithoutRequiringConsumersToParseFingerprint() {
        var metadata = GroupingMetadata.fromFingerprint(
                "npe|technical|null dereference|java.lang.nullpointerexception|com.acme.orderservice.load(orderservice.java)>com.acme.controller.get(controller.java)");

        assertThat(metadata.strategy()).isEqualTo("STACK_TRACE");
        assertThat(metadata.exceptionType()).isEqualTo("java.lang.nullpointerexception");
        assertThat(metadata.frames()).containsExactly(
                "com.acme.orderservice.load(orderservice.java)",
                "com.acme.controller.get(controller.java)");
        assertThat(metadata.lineNumbersIgnored()).isTrue();
    }

    @Test
    void fallsBackToDiagnosisGroupingWhenNoStackSignatureExists() {
        var metadata = GroupingMetadata.fromFingerprint("npe|technical|null dereference");

        assertThat(metadata.strategy()).isEqualTo("DIAGNOSIS");
        assertThat(metadata.exceptionType()).isEmpty();
        assertThat(metadata.frames()).isEmpty();
        assertThat(metadata.lineNumbersIgnored()).isTrue();
    }
}
