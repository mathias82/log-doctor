package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.engine.RuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaOperationalFailureRuleTest {

    private final KafkaOperationalFailureRule rule = new KafkaOperationalFailureRule();

    @TestFactory
    List<DynamicTest> coversEveryOperationalKafkaDiagnosis() {
        return List.of(
                sample("KAFKA_TOPIC_AUTHORIZATION_FAILED", "org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]"),
                sample("KAFKA_GROUP_AUTHORIZATION_FAILED", "org.apache.kafka.common.errors.GroupAuthorizationException: Not authorized to access group: billing"),
                sample("KAFKA_SASL_AUTHENTICATION_FAILED", "org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed during authentication due to invalid credentials"),
                sample("KAFKA_PRODUCER_FENCED", "org.apache.kafka.common.errors.ProducerFencedException: Producer attempted an operation with an old epoch"),
                sample("KAFKA_OUT_OF_ORDER_SEQUENCE", "org.apache.kafka.common.errors.OutOfOrderSequenceException: Out of order sequence number for producer"),
                sample("KAFKA_NOT_ENOUGH_REPLICAS", "org.apache.kafka.common.errors.NotEnoughReplicasException: Fewer in-sync replicas than required"),
                sample("KAFKA_RECORD_TOO_LARGE", "org.apache.kafka.common.errors.RecordTooLargeException: The message is 1200000 bytes"),
                sample("KAFKA_OFFSET_OUT_OF_RANGE", "org.apache.kafka.clients.consumer.OffsetOutOfRangeException: Fetch position 42 is out of range"),
                sample("KAFKA_REBALANCE_IN_PROGRESS", "org.apache.kafka.common.errors.RebalanceInProgressException: The group is rebalancing"),
                sample("KAFKA_UNKNOWN_TOPIC_OR_PARTITION", "org.apache.kafka.common.errors.UnknownTopicOrPartitionException: This server does not host this topic-partition"),
                sample("KAFKA_SCHEMA_REGISTRY_UNAUTHORIZED", "io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException: Unauthorized; error code: 401"),
                sample("KAFKA_SCHEMA_INCOMPATIBLE", "Schema Registry RestClientException: Incompatible schema for subject orders-value; error code: 409")
        ).stream()
                .map(sample -> DynamicTest.dynamicTest(sample.expectedType(), () -> {
                    Incident incident = match(sample.log());
                    assertThat(incident.type()).isEqualTo(sample.expectedType());
                    assertThat(incident.evidence()).isNotBlank();
                    assertThat(incident.recommendation()).isNotBlank();
                }))
                .toList();
    }

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
    void schemaRegistryUnauthorizedSupportsRealistic401Formatting() {
        var incident = match("Schema Registry request failed with status 401 Unauthorized for /subjects/orders-value");
        assertThat(incident.type()).isEqualTo("KAFKA_SCHEMA_REGISTRY_UNAUTHORIZED");
        assertThat(incident.category().name()).isEqualTo("SECURITY");
    }

    @Test
    void schemaRegistryIncompatibleSupports409ResponseFormatting() {
        var incident = match("io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException: schema is incompatible with an earlier schema; error code: 409");
        assertThat(incident.type()).isEqualTo("KAFKA_SCHEMA_INCOMPATIBLE");
    }

    @Test
    void doesNotTreatGenericUnauthorizedHttpTextAsSchemaRegistryFailure() {
        assertThat(rule.match(context("REST client returned 401 Unauthorized from payments API"))).isEmpty();
    }

    @Test
    void doesNotTreatSpringRestClientException401AsSchemaRegistryFailure() {
        assertThat(rule.match(context("org.springframework.web.client.RestClientException: 401 Unauthorized from payments API"))).isEmpty();
    }

    @Test
    void doesNotTreatUnrelatedRestClientException409AsSchemaCompatibilityFailure() {
        assertThat(rule.match(context("org.springframework.web.client.RestClientException: status 409 Conflict while updating customer"))).isEmpty();
    }

    @Test
    void doesNotTreatGenericIncompatibleSchemaTextAsKafkaFailure() {
        assertThat(rule.match(context("Application validation failed: incompatible schema between two internal JSON documents"))).isEmpty();
    }

    @Test
    void schemaRegistryPathStillProvidesEnoughContextFor401() {
        var incident = match("RestClientException: 401 Unauthorized calling /subjects/orders-value/versions/latest");
        assertThat(incident.type()).isEqualTo("KAFKA_SCHEMA_REGISTRY_UNAUTHORIZED");
    }

    @Test
    void ignoresUnrelatedApplicationFailure() {
        assertThat(rule.match(context("java.lang.RuntimeException: custom application failure"))).isEmpty();
    }

    private Case sample(String expectedType, String log) {
        return new Case(expectedType, log);
    }

    private Incident match(String text) {
        return rule.match(context(text)).orElseThrow();
    }

    private RuleContext context(String text) {
        return new RuleContext(null, null, text);
    }

    private record Case(String expectedType, String log) {}
}
