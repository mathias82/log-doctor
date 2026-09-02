package io.github.mathias82.logdoctor.core;

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
}
