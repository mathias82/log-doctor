package io.github.mathias82.logdoctor.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogRedactorTest {

    private final LogRedactor redactor = new LogRedactor();

    @Test
    void redactsCommonSecretsAndIdentifiers() {
        String input = """
                Authorization: Bearer abc.def.ghi
                password=hunter2
                api_key=secret-value
                user=john.doe@example.com
                remote=10.20.30.40
                https://example.test/path?token=my-token&x=1
                """;

        String result = redactor.redact(input);

        assertThat(result).doesNotContain("hunter2", "secret-value", "john.doe@example.com", "10.20.30.40", "my-token");
        assertThat(result).contains("<redacted>", "<redacted-email>", "<redacted-ip>");
    }

    @Test
    void redactsSecretsInsideJsonStyleLogs() {
        String input = """
                {"password":"hunter2","api_key":"secret-value","Authorization":"Bearer very-secret-token"}
                """;

        String result = redactor.redact(input);

        assertThat(result).doesNotContain("hunter2", "secret-value", "very-secret-token");
        assertThat(result).contains("<redacted>");
    }

    @Test
    void doesNotTreatInvalidIpv4LikeAnAddress() {
        String result = redactor.redact("version=999.999.999.999");

        assertThat(result).contains("999.999.999.999");
        assertThat(result).doesNotContain("<redacted-ip>");
    }
}
