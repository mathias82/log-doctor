package io.github.mathias82.logdoctor.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StackTraceFingerprint {
    private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+(?:[\\w.$-]+/)?([\\w.$]+)\\(([^()]*)\\)\\s*$");
    private static final Pattern EXCEPTION = Pattern.compile("(?:Caused by:\\s*)?([\\w.$]+(?:Exception|Error|Throwable))(?::.*)?$");
    private static final int MAX_FRAMES = 3;

    private StackTraceFingerprint() {}

    static String signature(String rawLog) {
        Metadata metadata = metadata(rawLog);
        if (!metadata.hasStackTraceSignal()) return "";
        return metadata.exceptionType() + "|" + String.join(">", metadata.frames());
    }

    static Metadata metadata(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) return Metadata.none();

        String currentException = "";
        String deepestException = "";
        List<String> currentFrames = new ArrayList<>();
        List<String> deepestFrames = new ArrayList<>();

        for (String line : rawLog.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Suppressed:")) continue;

            Matcher exception = EXCEPTION.matcher(trimmed);
            if (exception.find()) {
                currentException = exception.group(1).toLowerCase(Locale.ROOT);
                deepestException = currentException;
                currentFrames = new ArrayList<>();
                deepestFrames = currentFrames;
                continue;
            }

            Matcher frame = FRAME.matcher(line);
            if (frame.matches() && !currentException.isBlank() && currentFrames.size() < MAX_FRAMES) {
                currentFrames.add(normalizeFrame(frame.group(1), frame.group(2)));
            }
        }

        if (deepestException.isBlank()) return Metadata.none();
        return new Metadata(deepestException, List.copyOf(deepestFrames), true);
    }

    private static String normalizeFrame(String method, String source) {
        String normalizedSource = source.trim();
        if (normalizedSource.equals("Native Method") || normalizedSource.equals("Unknown Source")) {
            return (method + "(" + normalizedSource + ")").toLowerCase(Locale.ROOT);
        }
        normalizedSource = normalizedSource.replaceFirst(":\\d+$", "");
        return (method + "(" + normalizedSource + ")").toLowerCase(Locale.ROOT);
    }

    record Metadata(String exceptionType, List<String> frames, boolean lineNumbersIgnored) {
        static Metadata none() {
            return new Metadata("", List.of(), true);
        }

        boolean hasStackTraceSignal() {
            return exceptionType != null && !exceptionType.isBlank();
        }
    }
}
