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

        assertThat(StackTraceFingerprint.signature(first))
                .isEqualTo(StackTraceFingerprint.signature(second));
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

        assertThat(StackTraceFingerprint.signature(first))
                .isNotEqualTo(StackTraceFingerprint.signature(second));
    }

    @Test
    void usesDeepestVisibleExceptionType() {
        String log = """
                java.lang.IllegalStateException: wrapper
                    at com.acme.OrderService.load(OrderService.java:41)
                Caused by: java.net.SocketTimeoutException: timed out
                    at com.acme.Client.call(Client.java:21)
                """;

        assertThat(StackTraceFingerprint.signature(log)).startsWith("java.net.sockettimeoutexception|");
    }

    @Test
    void returnsEmptySignatureWithoutStackTraceSignal() {
        assertThat(StackTraceFingerprint.signature("ERROR request failed")).isEmpty();
    }
}
