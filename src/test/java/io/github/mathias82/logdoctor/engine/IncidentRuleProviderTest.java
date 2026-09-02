package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentRuleProviderTest {

    @Test
    void extensionRuleRunsBeforeBroadCommonCatalog() {
        IncidentRule extension = context -> context.contextText().contains("ClassNotFoundException")
                ? Optional.of(customIncident("CUSTOM_CLASSLOAD"))
                : Optional.empty();

        var detection = new IncidentDetector(List.of(extension))
                .detectDetailed(context("java.lang.ClassNotFoundException: com.acme.LegacyAdapter"));

        assertThat(detection).isPresent();
        assertThat(detection.orElseThrow().incident().type()).isEqualTo("CUSTOM_CLASSLOAD");
    }

    @Test
    void specializedBuiltInRuleKeepsPrecedenceOverExtension() {
        IncidentRule extension = context -> context.contextText().contains("NullPointerException")
                ? Optional.of(customIncident("CUSTOM_NPE"))
                : Optional.empty();

        var detection = new IncidentDetector(List.of(extension))
                .detectDetailed(context("java.lang.NullPointerException: order was null"));

        assertThat(detection).isPresent();
        assertThat(detection.orElseThrow().incident().type()).isNotEqualTo("CUSTOM_NPE");
    }

    @Test
    void failingExtensionRuleDoesNotBreakCatalogFallback() {
        IncidentRule broken = context -> {
            throw new IllegalStateException("plugin exploded");
        };

        var detection = new IncidentDetector(List.of(broken))
                .detectDetailed(context("java.lang.ClassNotFoundException: com.acme.LegacyAdapter"));

        assertThat(detection).isPresent();
        assertThat(detection.orElseThrow().incident().type()).isEqualTo("ClassNotFoundException");
    }

    @Test
    void nullOptionalFromExtensionIsTreatedAsNoMatch() {
        IncidentRule brokenContract = context -> null;

        var detection = new IncidentDetector(List.of(brokenContract))
                .detectDetailed(context("java.lang.ClassNotFoundException: com.acme.LegacyAdapter"));

        assertThat(detection).isPresent();
        assertThat(detection.orElseThrow().incident().type()).isEqualTo("ClassNotFoundException");
    }

    @Test
    void extensionRuleNameIsPreservedInDetectionEvidence() {
        class NamedExtensionRule implements IncidentRule {
            @Override
            public Optional<Incident> match(RuleContext context) {
                return Optional.of(customIncident("CUSTOM_NAMED"));
            }
        }

        var detection = new IncidentDetector(List.of(new NamedExtensionRule()))
                .detectDetailed(context("custom failure"))
                .orElseThrow();

        assertThat(detection.rule()).isEqualTo("NamedExtensionRule");
        assertThat(detection.reasons()).first().asString().contains("NamedExtensionRule");
    }

    @Test
    void providerCanExposeMultipleRules() {
        IncidentRule first = context -> Optional.empty();
        IncidentRule second = context -> Optional.of(customIncident("SECOND_RULE"));
        IncidentRuleProvider provider = () -> List.of(first, second);

        assertThat(provider.rules()).containsExactly(first, second);
    }

    @Test
    void ignoresNullExtensionListForEmbeddedCompatibility() {
        var detector = new IncidentDetector(null);

        assertThat(detector.detectDetailed(context("INFO application started"))).isEmpty();
    }

    private static RuleContext context(String text) {
        return new RuleContext(List.of(), null, text);
    }

    private static Incident customIncident(String type) {
        return new CatalogIncident(
                type,
                IncidentCategory.TECHNICAL,
                Severity.MEDIUM,
                Confidence.HIGH,
                "Custom extension",
                "Custom deterministic rule matched",
                "Organization-specific failure condition",
                "Review the custom rule guidance"
        );
    }
}
