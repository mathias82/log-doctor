package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.RuleContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaOperationalFailureRuleTest {

    private final KafkaOperationalFailureRule rule = new KafkaOperationalFailureRule();

    @Test
    void diagnosesTopicAuthorizationWithSecurityGuidance() {
        var incident = match("org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]");
        assertThat(incident.type()).isEqualTo("KAFKA_TOPIC_AUTHORIZATION_FAILED");
        assertThat(incident.category().name()).isEqualTo("SECURITY");
        assertThat(incident.recommendation()).contains("principal", "ACL");
    }

    @Test
    void diagnosesProducerFencingWithoutSuggestingBlindRetry() {
        var incident = match("org.apache.kafka.common.errors.ProducerFencedException: Producer attempted an operation with an old epoch");
        assertThat(incident.type()).isEqualTo("KAFKA_PRODUCER_FENCED");
        assertThat(incident.rootCause()).contains("transactional.id");
        assertThat(incident.recommendation()).contains("unique");
    }

    @Test
    void diagnosesInsufficientReplicasAsInfrastructureFailure() {
        var incident = match("org.apache.kafka.common.errors.NotEnoughReplicasException: Messages are rejected since there are fewer in-sync replicas than required");
        assertThat(incident.type()).isEqualTo("KAFKA_NOT_ENOUGH_REPLICAS");
        assertThat(incident.category().name()).isEqualTo("INFRASTRUCTURE");
        assertThat(incident.recommendation()).contains("ISR", "min.insync.replicas");
    }

    @Test
    void diagnosesOversizedRecordWithLimitGuidance() {
        var incident = match("org.apache.kafka.common.errors.RecordTooLargeException: The message is 1200000 bytes");
        assertThat(incident.type()).isEqualTo("KAFKA_RECORD_TOO_LARGE");
        assertThat(incident.recommendation()).contains("max.request.size");
    }

    @Test
    void diagnosesOffsetOutOfRangeWithExplicitRecoveryGuidance() {
        var incident = match("org.apache.kafka.clients.consumer.OffsetOutOfRangeException: Fetch position 42 is out of range");
        assertThat(incident.type()).isEqualTo("KAFKA_OFFSET_OUT_OF_RANGE");
        assertThat(incident.recommendation()).contains("reset strategy");
    }

    @Test
    void ignoresUnrelatedApplicationFailure() {
        assertThat(rule.match(context("java.lang.RuntimeException: custom application failure"))).isEmpty();
    }

    private Incident match(String text) {
        return rule.match(context(text)).orElseThrow();
    }

    private RuleContext context(String text) {
        return new RuleContext(null, null, text);
    }
}
