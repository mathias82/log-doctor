package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogBatchAnalyzerTest {
    private final LogBatchAnalyzer analyzer = new LogBatchAnalyzer(new DiagnosisEngine(new StubLlmClient()));

    @Test void groupsRepeatedFailuresByFingerprint(){String log="""
2026-09-01 14:32:17 ERROR request failed
java.lang.RuntimeException: order 123 failed
at com.acme.OrderService.run(OrderService.java:41)
2026-09-01 14:32:30 ERROR request failed
java.lang.RuntimeException: order 456 failed
at com.acme.OrderService.run(OrderService.java:41)
""";var r=analyzer.analyze(log);assertThat(r.failureBlocks()).isGreaterThan(0);assertThat(r.detectedFailureBlocks()).isEqualTo(2);assertThat(r.uniqueIncidents()).isGreaterThan(0);assertThat(r.incidents()).isNotEmpty();assertThat(r.incidents().getFirst().firstSeen()).isNotNull();assertThat(r.incidents().getFirst().lastSeen()).isNotNull();assertThat(r.truncated()).isFalse();assertThat(r.reportMarkdown()).contains("# Log Doctor Incident Report","## Incident groups");}

    @Test void exposesMatchAndCauseMetadataOnGroupedIncident(){String log="""
2026-09-01 14:32:17 ERROR request failed
java.lang.NullPointerException: order was null
    at com.acme.OrderService.run(OrderService.java:41)
""";var i=analyzer.analyze(log).incidents().getFirst();assertThat(i.matchScore()).isGreaterThan(0);assertThat(i.matchConfidence()).isNotBlank();assertThat(i.matchReasons()).isNotEmpty();assertThat(i.matchScoreFactors()).isNotEmpty();assertThat(i.causeChain()).isNotEmpty();assertThat(i.causeChain().getFirst().exceptionType()).contains("NullPointerException");}

    @Test void callsLlmOnceForRepeatedUnknownIncidentGroup(){CountingLlmClient llm=new CountingLlmClient();LogBatchAnalyzer a=new LogBatchAnalyzer(new DiagnosisEngine(llm));String log="""
2026-09-01 14:32:17 ERROR request failed
java.lang.RuntimeException: order 123 failed
at com.acme.OrderService.run(OrderService.java:41)
2026-09-01 14:32:30 ERROR request failed
java.lang.RuntimeException: order 456 failed
at com.acme.OrderService.run(OrderService.java:41)
""";var r=a.analyze(log);assertThat(r.detectedFailureBlocks()).isEqualTo(2);assertThat(r.uniqueIncidents()).isEqualTo(1);assertThat(llm.unknownCalls).isEqualTo(1);assertThat(llm.knownCalls).isZero();assertThat(r.incidents().getFirst().llmUsed()).isTrue();}

    @Test void doesNotTreatLevelWordInsideTimestampedMessageAsLogLevel(){String log="""
2026-09-01 14:32:17 ERROR request failed
java.lang.RuntimeException: boom
2026-09-01 14:32:17 worker reports INFO cache metadata
    at com.acme.OrderService.run(OrderService.java:41)
2026-09-01 14:32:18 INFO request completed
""";var r=analyzer.analyze(log);assertThat(r.detectedFailureBlocks()).isEqualTo(1);assertThat(r.incidents()).hasSize(1);assertThat(r.incidents().getFirst().evidence()).contains("reports INFO cache metadata");}

    @Test void recognizesLevelAfterTimestampAndThreadPrefix(){String log="""
2026-09-01 14:32:17 [worker-1] ERROR request failed
java.lang.RuntimeException: boom
2026-09-01 14:32:18 [worker-1] INFO request completed
""";var r=analyzer.analyze(log);assertThat(r.detectedFailureBlocks()).isEqualTo(1);assertThat(r.failureBlocks()).isEqualTo(1);}

    @Test void cleanTimestampedLogDoesNotCreateSyntheticFailureBlock(){String log="""
2026-09-01 14:32:17 INFO application started
2026-09-01 14:32:18 DEBUG cache warmed
2026-09-01 14:32:19 WARN slow request but recovered
""";var r=analyzer.analyze(log);assertThat(r.detectedFailureBlocks()).isZero();assertThat(r.failureBlocks()).isZero();assertThat(r.uniqueIncidents()).isZero();assertThat(r.incidents()).isEmpty();assertThat(r.truncated()).isFalse();}

    @Test void reportsDetectedBlocksBeyondProcessingCapAsTruncated(){StringBuilder log=new StringBuilder();for(int i=0;i<LogBatchAnalyzer.MAX_INCIDENT_BLOCKS+1;i++)log.append("2026-09-01 14:32:17 ERROR request failed\n").append("java.lang.RuntimeException: repeated failure\n");var r=analyzer.analyze(log.toString());assertThat(r.detectedFailureBlocks()).isEqualTo(LogBatchAnalyzer.MAX_INCIDENT_BLOCKS+1);assertThat(r.failureBlocks()).isEqualTo(LogBatchAnalyzer.MAX_INCIDENT_BLOCKS);assertThat(r.truncated()).isTrue();assertThat(r.reportMarkdown()).contains("Analysis truncated: true");}

    @Test void keepsCausedByStackTraceInsideParentFailureBlock(){String log="""
2026-09-01 14:32:17 ERROR request failed
java.lang.IllegalStateException: service failed
    at com.acme.OrderService.run(OrderService.java:41)
Caused by: java.net.SocketTimeoutException: downstream timeout
    at com.acme.Client.call(Client.java:19)
2026-09-01 14:32:18 INFO request completed
""";var r=analyzer.analyze(log);assertThat(r.detectedFailureBlocks()).isEqualTo(1);assertThat(r.failureBlocks()).isEqualTo(1);assertThat(r.correlations()).isEmpty();assertThat(r.rootCauseChains()).isEmpty();}

    @Test void keepsChronologicalFirstAndLastSeenForOutOfOrderOccurrences(){String log="""
2026-09-01 14:32:30 ERROR request failed
java.lang.RuntimeException: boom
2026-09-01 14:32:10 ERROR request failed
java.lang.RuntimeException: boom
2026-09-01 14:32:20 ERROR request failed
java.lang.RuntimeException: boom
""";var i=analyzer.analyze(log).incidents().getFirst();assertThat(i.firstSeen()).isEqualTo("2026-09-01 14:32:10");assertThat(i.lastSeen()).isEqualTo("2026-09-01 14:32:30");}

    @Test void doesNotOverwriteTimelineWithIncomparableTimestampBasis(){String log="""
2026-09-01T12:32:17Z ERROR request failed
java.lang.RuntimeException: boom
2026-09-01 15:32:20 ERROR request failed
java.lang.RuntimeException: boom
""";var r=analyzer.analyze(log);var i=r.incidents().getFirst();assertThat(i.firstSeen()).isEqualTo("2026-09-01T12:32:17Z");assertThat(i.lastSeen()).isEqualTo("2026-09-01T12:32:17Z");assertThat(r.correlations()).isEmpty();}

    @Test void derivesScoredRootCauseCandidateForConsecutiveDifferentFailuresWithinWindow(){String log="""
2026-09-01 14:32:17 ERROR first failure
java.lang.RuntimeException: boom
2026-09-01 14:32:40 ERROR second failure
java.net.SocketTimeoutException: downstream timeout
""";var r=analyzer.analyze(log);assertThat(r.failureBlocks()).isGreaterThanOrEqualTo(2);assertThat(r.correlations()).isNotEmpty();assertThat(r.rootCauseChains()).isNotEmpty();assertThat(r.rootCauseChains().getFirst().score()).isBetween(0,100);assertThat(r.rootCauseChains().getFirst().reason()).contains("not proven causation");}

    @Test void detectsBurstAgainstPerMinuteBaseline(){String log="""
2026-09-01 14:30:01 ERROR request failed
java.lang.RuntimeException: boom 1
2026-09-01 14:30:10 ERROR request failed
java.lang.RuntimeException: boom 2
2026-09-01 14:30:20 ERROR request failed
java.lang.RuntimeException: boom 3
2026-09-01 14:32:10 ERROR request failed
java.lang.RuntimeException: boom 4
""";var r=analyzer.analyze(log);assertThat(r.spikes()).isNotEmpty();assertThat(r.spikes().getFirst().count()).isEqualTo(3);assertThat(r.spikes().getFirst().multiplier()).isGreaterThanOrEqualTo(2.0);assertThat(r.reportMarkdown()).contains("## Spikes","3 events near");}

    @Test void doesNotInventCorrelationWhenTimestampsAreMissing(){String log="""
ERROR first failure
java.lang.RuntimeException: boom
ERROR second failure
java.net.SocketTimeoutException: downstream timeout
""";var r=analyzer.analyze(log);assertThat(r.detectedFailureBlocks()).isEqualTo(2);assertThat(r.correlations()).isEmpty();assertThat(r.rootCauseChains()).isEmpty();}
    @Test void doesNotCorrelateFailuresOutsideTimeWindow(){String log="""
2026-09-01 14:30:00 ERROR first failure
java.lang.RuntimeException: boom
2026-09-01 14:35:00 ERROR second failure
java.net.SocketTimeoutException: downstream timeout
""";var r=analyzer.analyze(log);assertThat(r.correlations()).isEmpty();assertThat(r.rootCauseChains()).isEmpty();}
    @Test void comparesOffsetTimestampsByInstant(){String log="""
2026-09-01T14:32:17+02:00 ERROR first failure
java.lang.RuntimeException: boom
2026-09-01T12:33:00Z ERROR second failure
java.net.SocketTimeoutException: downstream timeout
""";var r=analyzer.analyze(log);assertThat(r.correlations()).isNotEmpty();assertThat(r.rootCauseChains()).isNotEmpty();}
    @Test void returnsEmptyBatchForBlankInput(){var r=analyzer.analyze("   ");assertThat(r.failureBlocks()).isZero();assertThat(r.detectedFailureBlocks()).isZero();assertThat(r.uniqueIncidents()).isZero();assertThat(r.incidents()).isEmpty();assertThat(r.correlations()).isEmpty();assertThat(r.rootCauseChains()).isEmpty();assertThat(r.spikes()).isEmpty();assertThat(r.reportMarkdown()).contains("No log content was provided");assertThat(r.truncated()).isFalse();}

    private static class StubLlmClient implements LlmClient{@Override public String explainKnownIncident(Incident incident){return "known";}@Override public String analyzeUnknownLog(String rawLog,IncidentCategory category){return "unknown";}}
    private static final class CountingLlmClient extends StubLlmClient{private int knownCalls;private int unknownCalls;@Override public String explainKnownIncident(Incident incident){knownCalls++;return "known";}@Override public String analyzeUnknownLog(String rawLog,IncidentCategory category){unknownCalls++;return "unknown";}}
}
