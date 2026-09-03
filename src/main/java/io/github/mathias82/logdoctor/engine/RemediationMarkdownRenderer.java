package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.RemediationMetadata;
import io.github.mathias82.logdoctor.core.RemediationPlaybook;

/** Markdown rendering helpers for backend-owned remediation guidance. */
final class RemediationMarkdownRenderer {
    private RemediationMarkdownRenderer() {}

    static void append(StringBuilder out, RemediationMetadata remediation) {
        if (remediation == null) return;
        out.append("- Remediation safety: ").append(remediation.safety()).append('\n')
                .append("- Automatic execution allowed: ").append(remediation.automaticExecutionAllowed()).append('\n');
        if (!remediation.allowedActions().isEmpty()) {
            out.append("- Allowed action types: ").append(String.join(", ", remediation.allowedActions())).append('\n');
        }
        if (!remediation.verificationSteps().isEmpty()) {
            out.append("- Verification steps:\n");
            appendSteps(out, remediation.verificationSteps());
        }
        appendPlaybook(out, remediation.playbook());
    }

    private static void appendPlaybook(StringBuilder out, RemediationPlaybook playbook) {
        if (playbook == null) return;
        out.append("- Remediation playbook:\n");
        appendPhase(out, "Inspect evidence", playbook.inspect());
        appendPhase(out, "Change candidates", playbook.changeCandidates());
        appendPhase(out, "Validate recovery", playbook.validate());
        appendPhase(out, "Escalate when", playbook.escalationSignals());
    }

    private static void appendPhase(StringBuilder out, String title, java.util.List<String> steps) {
        out.append("  - **").append(title).append(":**\n");
        for (String step : steps) out.append("    - ").append(step).append('\n');
    }

    private static void appendSteps(StringBuilder out, java.util.List<String> steps) {
        for (String step : steps) out.append("  - ").append(step).append('\n');
    }
}
