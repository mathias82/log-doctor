package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRemediationPlaybookTest {
    @Test
    void reportIncludesAllBackendOwnedPlaybookPhases() {
        LogBatchAnalyzer analyzer = new LogBatchAnalyzer(new DiagnosisEngine(new NoopLlm()));
        String log = """
                2026-09-03 12:00:00 ERROR java.lang.OutOfMemoryError: Java heap space
                    at com.acme.Cache.load(Cache.java:42)
                """;

        String report = analyzer.analyze(log).reportMarkdown();

        assertThat(report)
                .contains("- Remediation playbook:")
                .contains("**Inspect evidence:**")
                .contains("Heap dump and GC logs")
                .contains("**Change candidates:**")
                .contains("**Validate recovery:**")
                .contains("**Escalate when:**")
                .contains("Automatic execution allowed: false");
    }

    private static final class NoopLlm implements LlmClient {
        @Override public String explainKnownIncident(Incident incident) { return ""; }
        @Override public String analyzeUnknownLog(String rawLog, IncidentCategory category) { return ""; }
    }
}
