package io.github.mathias82.logdoctor.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogLevelParserTest {

    @Test
    void parsesLeadingLevels() {
        assertThat(LogLevelParser.parse("ERROR request failed")).isEqualTo("ERROR");
        assertThat(LogLevelParser.parse("WARN: slow response")).isEqualTo("WARN");
        assertThat(LogLevelParser.parse("warning retrying")).isEqualTo("WARN");
    }

    @Test
    void parsesTimestampedLevelsWithThreadPrefixes() {
        assertThat(LogLevelParser.parse("2026-09-01 14:32:17 ERROR request failed")).isEqualTo("ERROR");
        assertThat(LogLevelParser.parse("2026-09-01T14:32:17.123+03:00 [worker-1] [trace-42] INFO completed"))
                .isEqualTo("INFO");
        assertThat(LogLevelParser.parse("2026-09-01T11:32:17Z [worker] WARNING retrying"))
                .isEqualTo("WARN");
    }

    @Test
    void ignoresLevelWordsInsideMessageText() {
        assertThat(LogLevelParser.parse("2026-09-01 14:32:17 worker reports INFO cache metadata")).isNull();
        assertThat(LogLevelParser.parse("cache returned ERROR metadata")).isNull();
        assertThat(LogLevelParser.parse("java.lang.RuntimeException: INFO is just payload text")).isNull();
    }

    @Test
    void ignoresStackTraceAndContinuationLines() {
        assertThat(LogLevelParser.parse("    at com.acme.Service.run(Service.java:42)")).isNull();
        assertThat(LogLevelParser.parse("Caused by: java.lang.IllegalStateException: ERROR state")).isNull();
        assertThat(LogLevelParser.parse("Suppressed: java.io.IOException: WARN marker")).isNull();
    }
}
