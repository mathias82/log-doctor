package io.github.mathias82.logdoctor.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceFingerprintTest {

    @Test
    void ignoresLineNumbersButKeepsCallPath() {
        String first = """
                java.lang.IllegalStateException: boom
                    at com.acme.OrderService.load(OrderService.java:41)
                    at com.acme.OrderController.get(OrderController.java:18)
                """;
        String second = """
                java.lang.IllegalStateException: boom again
                    at com.acme.OrderService.load(OrderService.java:97)
                    at com.acme.OrderController.get(OrderController.java:33)
                """;

        assertThat(StackTraceFingerprint.signature(first)).isEqualTo(StackTraceFingerprint.signature(second));
    }

    @Test
    void exposesStructuredMetadataWithoutReparsingSignatureDelimiters() {
        String log = """
                java.lang.IllegalStateException: wrapper
                    at com.acme.OrderService.load(OrderService.java:41)
                Caused by: java.net.SocketTimeoutException: timed out
                    at com.acme.Client.call(Client.java:21)
                    at com.acme.HttpTransport.send(HttpTransport.java:12)
                """;

        var metadata = StackTraceFingerprint.metadata(log);

        assertThat(metadata.exceptionType()).isEqualTo("java.net.sockettimeoutexception");
        assertThat(metadata.frames()).containsExactly(
                "com.acme.client.call(client.java)",
                "com.acme.httptransport.send(httptransport.java)");
        assertThat(metadata.lineNumbersIgnored()).isTrue();
        assertThat(metadata.hasStackTraceSignal()).isTrue();
    }

    @Test
    void separatesSameExceptionFromDifferentCallSites() {
        String first = """
                java.lang.IllegalStateException: boom
                    at com.acme.OrderService.load(OrderService.java:41)
                """;
        String second = """
                java.lang.IllegalStateException: boom
                    at com.acme.PaymentService.charge(PaymentService.java:41)
                """;

        assertThat(StackTraceFingerprint.signature(first)).isNotEqualTo(StackTraceFingerprint.signature(second));
    }

    @Test
    void associatesFramesWithDeepestVisibleCause() {
        String log = """
                java.lang.IllegalStateException: wrapper
                    at com.acme.OrderService.load(OrderService.java:41)
                    at com.acme.OrderController.get(OrderController.java:18)
                Caused by: java.net.SocketTimeoutException: timed out
                    at com.acme.Client.call(Client.java:21)
                    at com.acme.HttpTransport.send(HttpTransport.java:12)
                """;

        assertThat(StackTraceFingerprint.signature(log))
                .isEqualTo("java.net.sockettimeoutexception|com.acme.client.call(client.java)>com.acme.httptransport.send(httptransport.java)");
    }

    @Test
    void supportsThrowableTypes() {
        String log = """
                com.acme.FatalThrowable: fatal
                    at com.acme.Worker.run(Worker.java:8)
                """;
        assertThat(StackTraceFingerprint.signature(log)).startsWith("com.acme.fatalthrowable|");
    }

    @Test
    void supportsNativeAndUnknownSourceFrames() {
        String log = """
                java.lang.IllegalStateException: boom
                    at java.base/java.lang.Thread.run(Native Method)
                    at com.acme.Generated.invoke(Unknown Source)
                """;
        assertThat(StackTraceFingerprint.signature(log))
                .contains("java.lang.thread.run(native method)")
                .contains("com.acme.generated.invoke(unknown source)");
    }

    @Test
    void ignoresSuppressedExceptionWhenChoosingDeepestCause() {
        String log = """
                java.lang.IllegalStateException: wrapper
                    at com.acme.OrderService.load(OrderService.java:41)
                Suppressed: java.io.IOException: cleanup failed
                    at com.acme.Cleanup.close(Cleanup.java:7)
                Caused by: java.net.SocketTimeoutException: timed out
                    at com.acme.Client.call(Client.java:21)
                """;
        assertThat(StackTraceFingerprint.signature(log))
                .startsWith("java.net.sockettimeoutexception|com.acme.client.call(client.java)");
    }

    @Test
    void returnsEmptyMetadataWithoutStackTraceSignal() {
        var metadata = StackTraceFingerprint.metadata("ERROR request failed");
        assertThat(StackTraceFingerprint.signature("ERROR request failed")).isEmpty();
        assertThat(metadata.hasStackTraceSignal()).isFalse();
        assertThat(metadata.frames()).isEmpty();
    }
}
