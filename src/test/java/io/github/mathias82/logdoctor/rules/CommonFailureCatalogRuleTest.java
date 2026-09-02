package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.RuleContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommonFailureCatalogRuleTest {

    private final CommonFailureCatalogRule rule = new CommonFailureCatalogRule();

    @Test
    void containsBroadCuratedCoverage() {
        assertThat(CommonFailureCatalogRule.catalogSize()).isGreaterThanOrEqualTo(80);
    }

    @Test
    void detectsJvmLinkageFailure() {
        Incident incident = detect("java.lang.NoClassDefFoundError: com/acme/Foo");

        assertThat(incident.type()).isEqualTo("NoClassDefFoundError");
        assertThat(incident.category()).isEqualTo(IncidentCategory.CONFIGURATION);
        assertThat(incident.severity()).isEqualTo(Severity.HIGH);
        assertThat(incident.component()).isEqualTo("JVM");
        assertThat(incident.evidence()).contains("NoClassDefFoundError");
    }

    @Test
    void detectsSpringBeanFailure() {
        Incident incident = detect("org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'orders'");

        assertThat(incident.type()).isEqualTo("BeanCreationException");
        assertThat(incident.component()).isEqualTo("Spring");
    }

    @Test
    void detectsHibernateSqlFailure() {
        Incident incident = detect("org.hibernate.exception.SQLGrammarException: could not execute query");

        assertThat(incident.type()).isEqualTo("SQLGrammarException");
        assertThat(incident.category()).isEqualTo(IncidentCategory.DATABASE);
        assertThat(incident.component()).isEqualTo("Hibernate/JDBC");
    }

    @Test
    void detectsKafkaAuthorizationFailure() {
        Incident incident = detect("org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]");

        assertThat(incident.type()).isEqualTo("TopicAuthorizationException");
        assertThat(incident.category()).isEqualTo(IncidentCategory.SECURITY);
        assertThat(incident.component()).isEqualTo("Kafka");
    }

    @Test
    void detectsKafkaTransactionFencing() {
        Incident incident = detect("org.apache.kafka.common.errors.ProducerFencedException: Producer attempted an operation with an old epoch");

        assertThat(incident.type()).isEqualTo("ProducerFencedException");
        assertThat(incident.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(incident.component()).isEqualTo("Kafka Transactions");
    }

    @Test
    void detectsSchemaRegistryFailure() {
        Incident incident = detect("io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException: Subject not found");

        assertThat(incident.type()).isEqualTo("SchemaRegistrySubjectNotFound");
        assertThat(incident.component()).isEqualTo("Schema Registry");
    }

    @Test
    void matchingIsCaseInsensitive() {
        Incident incident = detect("org.apache.kafka.common.errors.topicauthorizationexception: denied");
        assertThat(incident.type()).isEqualTo("TopicAuthorizationException");
    }

    @Test
    void ignoresUnknownLogs() {
        Optional<Incident> incident = rule.match(context("ERROR custom.acme.UnknownBusinessFailure: boom"));
        assertThat(incident).isEmpty();
    }

    private Incident detect(String log) {
        return rule.match(context(log)).orElseThrow();
    }

    private RuleContext context(String log) {
        return new RuleContext(null, null, log);
    }
}
