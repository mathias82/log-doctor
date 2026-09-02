package io.github.mathias82.logdoctor.engine;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRuleQualityMatrixTest {

    private final IncidentDetector detector = new IncidentDetector();

    @TestFactory
    Stream<DynamicTest> matchesRepresentativeFailuresWithExpectedRule() {
        return List.of(
                new Case("JVM heap OOM", "java.lang.OutOfMemoryError: Java heap space", "OutOfMemoryRule"),
                new Case("JVM stack overflow", "java.lang.StackOverflowError\n\tat com.acme.Tree.walk(Tree.java:41)", "CommonFailureCatalogRule"),
                new Case("Null pointer", "java.lang.NullPointerException: order was null", "NullPointerExceptionRule"),
                new Case("Hibernate lazy init", "org.hibernate.LazyInitializationException: could not initialize proxy - no Session", "HibernateLazyInitRule"),
                new Case("Hikari timeout", "HikariPool-1 - Connection is not available, request timed out after 30000ms", "HikariTimeoutRule"),
                new Case("Spring bind failure", "Failed to bind properties under 'server.port' to java.lang.Integer", "SpringConfigBindRule"),
                new Case("Spring missing bean", "org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.acme.PaymentClient' available", "MissingSpringBeanRule"),
                new Case("Spring Boot startup analysis", "APPLICATION FAILED TO START\n\nDescription:\nApplication context could not start.\n\nAction:\nCheck configuration.", "SpringBootStartupFailureRule"),
                new Case("JPA optimistic locking", "jakarta.persistence.OptimisticLockException: Row was updated or deleted by another transaction", "PersistenceConcurrencyRule"),
                new Case("Kafka authorization", "org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]", "KafkaOperationalFailureRule"),
                new Case("Kafka rebalance", "org.apache.kafka.clients.consumer.CommitFailedException: Commit cannot be completed since the group has already rebalanced", "KafkaOperationalFailureRule"),
                new Case("Kafka unknown topic", "org.apache.kafka.common.errors.UnknownTopicOrPartitionException: This server does not host this topic-partition", "KafkaOperationalFailureRule")
        ).stream().map(testCase -> DynamicTest.dynamicTest(testCase.name(), () -> {
            var detection = detector.detectDetailed(context(testCase.log()));
            assertThat(detection).as(testCase.name()).isPresent();
            assertThat(detection.orElseThrow().rule()).isEqualTo(testCase.expectedRule());
            assertThat(detection.orElseThrow().reasons()).isNotEmpty();
        }));
    }

    @TestFactory
    Stream<DynamicTest> avoidsRepresentativeFalsePositives() {
        return List.of(
                "INFO health check completed successfully",
                "INFO Kafka consumer started for topic orders",
                "DEBUG retrying HTTP request after normal backoff",
                "INFO application profile is production",
                "WARN cache miss for key customer-42"
        ).stream().map(log -> DynamicTest.dynamicTest(log, () ->
                assertThat(detector.detectDetailed(context(log))).isEmpty()));
    }

    private static RuleContext context(String log) {
        return new RuleContext(List.of(), null, log);
    }

    private record Case(String name, String log, String expectedRule) {}
}
