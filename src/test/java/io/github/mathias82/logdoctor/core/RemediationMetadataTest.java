package io.github.mathias82.logdoctor.core;

import io.github.mathias82.logdoctor.incidents.CatalogIncident;
import io.github.mathias82.logdoctor.incidents.HikariTimeoutIncident;
import io.github.mathias82.logdoctor.incidents.OutOfMemoryIncident;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemediationMetadataTest {

    @Test
    void keepsInfrastructureRemediationHumanReviewed() {
        var metadata = RemediationMetadata.from(
                IncidentCategory.INFRASTRUCTURE,
                FixPolicy.allowedFixes(IncidentCategory.INFRASTRUCTURE)
        );

        assertThat(metadata.safety()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(metadata.allowedActions()).containsExactly("NO_AUTOMATIC_FIX");
        assertThat(metadata.verificationSteps()).contains("Confirm dependency health and connectivity");
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }

    @Test
    void exposesReviewableConfigurationActionsWithoutEnablingExecution() {
        var metadata = RemediationMetadata.from(
                IncidentCategory.CONFIGURATION,
                FixPolicy.allowedFixes(IncidentCategory.CONFIGURATION)
        );

        assertThat(metadata.safety()).isEqualTo("REVIEW_BEFORE_APPLY");
        assertThat(metadata.allowedActions()).containsExactly("SPRING_CONFIG");
        assertThat(metadata.verificationSteps()).hasSize(2);
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }

    @Test
    void threadingRemainsHumanReviewedWhenPolicyContainsNoAutomaticFix() {
        var metadata = RemediationMetadata.from(
                IncidentCategory.THREADING,
                FixPolicy.allowedFixes(IncidentCategory.THREADING)
        );

        assertThat(metadata.safety()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(metadata.allowedActions()).contains("JAVA_CODE", "NO_AUTOMATIC_FIX");
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }

    @Test
    void givesHikariSpecificVerificationWithoutChangingSafetyPolicy() {
        var incident = new HikariTimeoutIncident();
        var metadata = RemediationMetadata.from(incident, FixPolicy.allowedFixes(incident.category()));

        assertThat(metadata.verificationSteps())
                .contains("Inspect HikariCP active, idle and pending connection metrics during the failure window")
                .anyMatch(step -> step.contains("leaked or long-running database connections"));
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }

    @Test
    void givesOutOfMemorySpecificEvidenceChecks() {
        var incident = new OutOfMemoryIncident();
        var metadata = RemediationMetadata.from(incident, FixPolicy.allowedFixes(incident.category()));

        assertThat(metadata.verificationSteps())
                .anyMatch(step -> step.contains("heap dump"))
                .anyMatch(step -> step.contains("container or host memory limits"));
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }

    @Test
    void givesKafkaAuthorizationSpecificVerificationAndKeepsHumanReview() {
        var incident = new CatalogIncident(
                "KAFKA_TOPIC_AUTHORIZATION_FAILED",
                IncidentCategory.SECURITY,
                Severity.HIGH,
                Confidence.HIGH,
                "Kafka ACL",
                "Kafka topic authorization failed",
                "Principal is not authorized",
                "Verify ACLs"
        );
        var metadata = RemediationMetadata.from(incident, FixPolicy.allowedFixes(incident.category()));

        assertThat(metadata.safety()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(metadata.verificationSteps())
                .anyMatch(step -> step.contains("authenticated Kafka or Schema Registry principal"))
                .anyMatch(step -> step.contains("without weakening security controls"));
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }

    @Test
    void givesSpringBootStartupSpecificVerification() {
        var incident = new CatalogIncident(
                "SPRING_BOOT_STARTUP_FAILURE",
                IncidentCategory.CONFIGURATION,
                Severity.HIGH,
                Confidence.HIGH,
                "Spring Boot startup",
                "Spring Boot failed during application startup",
                "Configuration failed",
                "Review configuration"
        );
        var metadata = RemediationMetadata.from(incident, FixPolicy.allowedFixes(incident.category()));

        assertThat(metadata.verificationSteps())
                .anyMatch(step -> step.contains("FailureAnalysis Description/Action"))
                .anyMatch(step -> step.contains("same profile, environment and external dependencies"));
        assertThat(metadata.automaticExecutionAllowed()).isFalse();
    }
}
