package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;

import java.util.Locale;
import java.util.Optional;

/**
 * Preserves the conservative human-review semantics for persistence/concurrency
 * failures before the broad deterministic catalog is evaluated.
 */
public final class PersistenceConcurrencyRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        String log = ctx.contextText();
        String lower = log.toLowerCase(Locale.ROOT);

        if (!isConcurrencyFailure(lower)) {
            return Optional.empty();
        }

        CatalogIncident incident = new CatalogIncident(
                "CONCURRENCY_FAILURE",
                IncidentCategory.THREADING,
                Severity.HIGH,
                Confidence.HIGH,
                "Persistence concurrency",
                "Concurrency / data consistency failure",
                "Concurrent database access changed or locked state expected by this operation.",
                "Investigate transaction boundaries, lock ordering and retry semantics; do not apply an automatic fix."
        );
        incident.setEvidence(log.lines()
                .filter(line -> {
                    String normalized = line.toLowerCase(Locale.ROOT);
                    return isConcurrencyFailure(normalized);
                })
                .findFirst()
                .orElse("Persistence concurrency failure detected"));
        return Optional.of(incident);
    }

    private static boolean isConcurrencyFailure(String lower) {
        return lower.contains("optimisticlock")
                || lower.contains("staleobjectstate")
                || lower.contains("deadlock")
                || lower.contains("could not serialize access");
    }
}
