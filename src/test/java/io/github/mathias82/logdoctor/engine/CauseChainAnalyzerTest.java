package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.LogLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CauseChainAnalyzerTest {

    private final CauseChainAnalyzer analyzer = new CauseChainAnalyzer();

    @Test
    void extractsOuterToDeepestCauseOrder() {
        List<LogLine> lines = List.of(
                new LogLine(1, null, "ERROR", "org.springframework.beans.factory.BeanCreationException: failed to create orders"),
                new LogLine(2, null, null, "at com.acme.App.start(App.java:10)"),
                new LogLine(3, null, null, "Caused by: java.net.ConnectException: Connection refused"),
                new LogLine(4, null, null, "at com.acme.Client.call(Client.java:20)"),
                new LogLine(5, null, null, "Caused by: java.net.SocketTimeoutException: connect timed out")
        );

        var causes = analyzer.analyze(lines);

        assertThat(causes).hasSize(3);
        assertThat(causes.get(0).exceptionType()).endsWith("BeanCreationException");
        assertThat(causes.get(1).exceptionType()).isEqualTo("java.net.ConnectException");
        assertThat(causes.get(2).exceptionType()).isEqualTo("java.net.SocketTimeoutException");
        assertThat(causes.get(2).message()).isEqualTo("connect timed out");
    }

    @Test
    void ignoresSuppressedExceptions() {
        List<LogLine> lines = List.of(
                new LogLine(1, null, "ERROR", "java.lang.RuntimeException: failed"),
                new LogLine(2, null, null, "Suppressed: java.io.IOException: cleanup failed"),
                new LogLine(3, null, null, "Caused by: java.lang.IllegalArgumentException: bad input")
        );

        assertThat(analyzer.analyze(lines))
                .extracting(CauseChainAnalyzer.Cause::exceptionType)
                .containsExactly("java.lang.RuntimeException", "java.lang.IllegalArgumentException");
    }
}
