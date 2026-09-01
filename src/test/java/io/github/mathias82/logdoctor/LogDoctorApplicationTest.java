package io.github.mathias82.logdoctor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogDoctorApplicationTest {

    @Test
    void usesDefaultWebPort() {
        assertThat(LogDoctorApplication.resolveWebPort(new String[]{"--web"})).isEqualTo(8080);
    }

    @Test
    void parsesSeparatePortArgument() {
        assertThat(LogDoctorApplication.resolveWebPort(new String[]{"--web", "--port", "9090"})).isEqualTo(9090);
    }

    @Test
    void parsesInlinePortArgument() {
        assertThat(LogDoctorApplication.resolveWebPort(new String[]{"--web", "--port=9091"})).isEqualTo(9091);
    }

    @Test
    void parsesSeparateHostArgument() {
        assertThat(LogDoctorApplication.resolveBindAddress(new String[]{"--web", "--host", "0.0.0.0"}))
                .isEqualTo("0.0.0.0");
    }

    @Test
    void parsesInlineHostArgument() {
        assertThat(LogDoctorApplication.resolveBindAddress(new String[]{"--web", "--host=127.0.0.1"}))
                .isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsMissingOrBlankHost() {
        assertThatThrownBy(() -> LogDoctorApplication.resolveBindAddress(new String[]{"--web", "--host"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogDoctorApplication.resolveBindAddress(new String[]{"--web", "--host="}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidPorts() {
        assertThatThrownBy(() -> LogDoctorApplication.resolveWebPort(new String[]{"--web", "--port", "0"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogDoctorApplication.resolveWebPort(new String[]{"--web", "--port=70000"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogDoctorApplication.resolveWebPort(new String[]{"--web", "--port", "abc"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
