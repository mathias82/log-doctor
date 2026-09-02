package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.RuleContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootStartupFailureRuleTest {

    private final SpringBootStartupFailureRule rule = new SpringBootStartupFailureRule();

    @Test
    void extractsSpringBootDescriptionAndAction() {
        String log = """
                ***************************
                APPLICATION FAILED TO START
                ***************************

                Description:

                Parameter 0 of constructor in com.acme.OrderService required a bean of type 'com.acme.PaymentClient' that could not be found.

                Action:

                Consider defining a bean of type 'com.acme.PaymentClient' in your configuration.
                """;

        Incident incident = rule.match(context(log)).orElseThrow();

        assertThat(incident.type()).isEqualTo("SPRING_BOOT_STARTUP_FAILURE");
        assertThat(incident.category()).isEqualTo(IncidentCategory.CONFIGURATION);
        assertThat(incident.severity()).isEqualTo(Severity.HIGH);
        assertThat(incident.component()).isEqualTo("Spring Boot startup");
        assertThat(incident.rootCause()).contains("PaymentClient").contains("could not be found");
        assertThat(incident.recommendation()).contains("Consider defining a bean");
        assertThat(incident.evidence()).contains("APPLICATION FAILED TO START").contains("Description:");
    }

    @Test
    void fallsBackToDeepestCauseWhenFailureAnalysisSectionsAreMissing() {
        String log = """
                APPLICATION FAILED TO START
                org.springframework.context.ApplicationContextException: Unable to start web server
                Caused by: org.springframework.boot.web.server.PortInUseException: Port 8080 is already in use
                """;

        Incident incident = rule.match(context(log)).orElseThrow();

        assertThat(incident.rootCause()).contains("PortInUseException").contains("8080");
        assertThat(incident.recommendation()).contains("Inspect the deepest cause");
    }

    @Test
    void ignoresOrdinaryRuntimeFailuresWithoutStartupMarker() {
        assertThat(rule.match(context("java.lang.RuntimeException: request failed"))).isEmpty();
    }

    private static RuleContext context(String log) {
        return new RuleContext(null, null, log);
    }
}
