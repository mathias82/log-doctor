package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Diagnoses Spring Boot FailureAnalysis output when a more specific rule has not
 * already matched. This keeps the framework wrapper from hiding the actionable
 * Description/Action emitted during startup.
 */
public final class SpringBootStartupFailureRule implements IncidentRule {

    private static final String STARTUP_MARKER = "APPLICATION FAILED TO START";

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        String log = ctx.contextText();
        if (log == null || log.isBlank() || !log.toUpperCase(Locale.ROOT).contains(STARTUP_MARKER)) {
            return Optional.empty();
        }

        String description = section(log, "Description:", "Action:");
        String action = section(log, "Action:", null);
        String rootCause = description.isBlank()
                ? deepestUsefulLine(log)
                : description;
        String recommendation = action.isBlank()
                ? "Inspect the deepest cause and Spring Boot startup configuration before restarting the application."
                : action;

        CatalogIncident incident = new CatalogIncident(
                "SPRING_BOOT_STARTUP_FAILURE",
                IncidentCategory.CONFIGURATION,
                Severity.HIGH,
                Confidence.HIGH,
                "Spring Boot startup",
                "Spring Boot failed during application startup.",
                rootCause,
                recommendation
        );
        incident.setEvidence(evidence(log, description));
        return Optional.of(incident);
    }

    private static String section(String log, String startMarker, String endMarker) {
        List<String> lines = log.lines().toList();
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equalsIgnoreCase(startMarker)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return "";

        List<String> content = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (endMarker != null && line.equalsIgnoreCase(endMarker)) break;
            if (!line.isBlank() && !isSeparator(line)) content.add(line);
        }
        return String.join(" ", content).trim();
    }

    private static boolean isSeparator(String line) {
        return line.chars().allMatch(ch -> ch == '*' || ch == '-' || Character.isWhitespace(ch));
    }

    private static String deepestUsefulLine(String log) {
        return log.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("Caused by:"))
                .reduce((first, second) -> second)
                .orElse("Spring Boot emitted APPLICATION FAILED TO START without a structured Description section.");
    }

    private static String evidence(String log, String description) {
        String marker = log.lines()
                .map(String::trim)
                .filter(line -> line.equalsIgnoreCase(STARTUP_MARKER))
                .findFirst()
                .orElse(STARTUP_MARKER);
        return description.isBlank() ? marker : marker + System.lineSeparator() + "Description: " + description;
    }
}
