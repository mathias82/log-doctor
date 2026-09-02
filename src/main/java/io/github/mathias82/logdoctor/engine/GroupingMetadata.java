package io.github.mathias82.logdoctor.engine;

import java.util.Arrays;
import java.util.List;

/**
 * Structured presentation contract for incident grouping.
 *
 * <p>The opaque fingerprint remains the stable identity. Consumers should use this
 * metadata instead of reverse-parsing fingerprint delimiters.</p>
 */
public record GroupingMetadata(
        String strategy,
        String exceptionType,
        List<String> frames,
        boolean lineNumbersIgnored
) {
    public static GroupingMetadata fromFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return diagnosisOnly();
        }
        String[] parts = fingerprint.split("\\|", -1);
        if (parts.length < 4 || parts[3].isBlank()) {
            return diagnosisOnly();
        }
        List<String> frames = parts.length < 5 || parts[4].isBlank()
                ? List.of()
                : Arrays.stream(parts[4].split(">"))
                        .filter(frame -> !frame.isBlank())
                        .toList();
        return new GroupingMetadata("STACK_TRACE", parts[3], frames, true);
    }

    public static GroupingMetadata diagnosisOnly() {
        return new GroupingMetadata("DIAGNOSIS", "", List.of(), true);
    }
}
