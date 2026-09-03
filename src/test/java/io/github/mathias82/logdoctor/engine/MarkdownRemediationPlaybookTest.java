package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.RemediationMetadata;
import io.github.mathias82.logdoctor.core.RemediationPlaybook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRemediationPlaybookTest {
    @Test
    void rendersAllBackendOwnedPlaybookPhasesWithoutChangingSafetyPolicy() {
        RemediationPlaybook playbook = new RemediationPlaybook(
                List.of("Inspect heap dump and GC logs"),
                List.of("Remove the confirmed retention source"),
                List.of("Repeat representative load"),
                List.of("Escalate if memory growth remains unexplained"));
        RemediationMetadata remediation = new RemediationMetadata(
                "HUMAN_REVIEW_REQUIRED",
                List.of("NO_AUTOMATIC_FIX"),
                List.of("Verify memory pressure after remediation"),
                false,
                playbook);

        StringBuilder report = new StringBuilder();
        RemediationMarkdownRenderer.append(report, remediation);

        assertThat(report.toString())
                .contains("Remediation safety: HUMAN_REVIEW_REQUIRED")
                .contains("Automatic execution allowed: false")
                .contains("- Remediation playbook:")
                .contains("**Inspect evidence:**")
                .contains("Inspect heap dump and GC logs")
                .contains("**Change candidates:**")
                .contains("**Validate recovery:**")
                .contains("**Escalate when:**");
    }
}
