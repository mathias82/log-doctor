package io.github.mathias82.logdoctor.core;

import java.util.List;

public record RemediationMetadata(
        String safety,
        List<String> allowedActions,
        List<String> verificationSteps,
        boolean automaticExecutionAllowed
) {
    public static RemediationMetadata from(IncidentCategory category, java.util.Set<FixType> allowedFixes) {
        boolean manualOnly = allowedFixes.contains(FixType.NO_AUTOMATIC_FIX);
        List<String> actions = allowedFixes.stream().map(Enum::name).sorted().toList();
        List<String> verification = switch (category) {
            case DESERIALIZATION -> List.of("Re-run the failing payload against the expected schema", "Confirm producer and consumer schema compatibility");
            case CONFIGURATION -> List.of("Validate the effective runtime configuration", "Restart only after reviewing the configuration diff");
            case MEMORY -> List.of("Capture heap/GC evidence before changing limits", "Verify memory pressure after the change under representative load");
            case DATABASE -> List.of("Reproduce against the affected query or transaction", "Verify transaction and persistence behavior after the code change");
            case THREADING -> List.of("Reproduce under concurrent load", "Verify ordering, locking and retry behavior before rollout");
            case INFRASTRUCTURE -> List.of("Confirm dependency health and connectivity", "Review infrastructure telemetry before remediation");
            case BUSINESS -> List.of("Validate the domain invariant with an owner", "Confirm expected state transitions before changing behavior");
            case SECURITY -> List.of("Escalate to a security owner", "Validate authorization/authentication evidence before any change");
            default -> List.of("Reproduce the failure", "Verify the diagnosis with focused tests before rollout");
        };
        return new RemediationMetadata(manualOnly ? "HUMAN_REVIEW_REQUIRED" : "REVIEW_BEFORE_APPLY", actions, verification, false);
    }
}
