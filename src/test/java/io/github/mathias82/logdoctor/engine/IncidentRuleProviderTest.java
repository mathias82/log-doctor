package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;
import org.junit.jupiter.api.Test;
import io.github.mathias82.logdoctor.observability.RuntimeMetrics;

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
        var failureCount = new int[]{0};
        RuleFailureListener listener = () -> failureCount[0]++;

        var detection = new IncidentDetector(List.of(broken), listener)
                .detectDetailed(context("java.lang.ClassNotFoundException: com.acme.LegacyAdapter"));
        assertThat(detection).isPresent();
        assertThat(detection.orElseThrow().incident().type()).isEqualTo("ClassNotFoundException");
        assertThat(failureCount[0]).isEqualTo(1);
    }

    @Test
    void failingExtensionRuleUpdatesRuntimeMetrics() {
    IncidentRule broken = context -> {
        throw new IllegalStateException("plugin exploded");
    };

    RuntimeMetrics metrics = new RuntimeMetrics();

    var detection = new IncidentDetector(
            List.of(broken),
            metrics::recordRuleProviderFailure
    ).detectDetailed(
            context("java.lang.ClassNotFoundException: com.acme.LegacyAdapter")
    );

    assertThat(detection).isPresent();
    assertThat(metrics.snapshot().ruleProviderFailures()).isEqualTo(1);
    assertThat(metrics.asMap().get("ruleProviderFailures")).isEqualTo(1L);
    assertThat(metrics.prometheusText())
            .contains("log_doctor_rule_provider_failures_total 1");

}

    @Test
void failingProviderNotifiesFailureListener() {
    var failureCount = new int[]{0};
    RuleFailureListener listener = () -> failureCount[0]++;

    new IncidentDetector(listener);

    assertThat(failureCount[0]).isEqualTo(1);
}
@Test
void failingProviderUpdatesRuntimeMetrics() {
    RuntimeMetrics metrics = new RuntimeMetrics();

    new IncidentDetector(metrics::recordRuleProviderFailure);

    assertThat(metrics.snapshot().ruleProviderFailures()).isEqualTo(1);
    assertThat(metrics.asMap().get("ruleProviderFailures")).isEqualTo(1L);
    assertThat(metrics.prometheusText())
            .contains("log_doctor_rule_provider_failures_total 1");
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
        var detector = new IncidentDetector((List<IncidentRule>) null);

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
