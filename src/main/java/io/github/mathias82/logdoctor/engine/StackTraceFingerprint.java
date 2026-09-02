package io.github.mathias82.logdoctor.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StackTraceFingerprint {
    private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+([\\w.$]+)\\(([^:()]+)(?::\\d+)?\\)\\s*$");
    private static final Pattern EXCEPTION = Pattern.compile("(?:Caused by:\\s*)?([\\w.$]+(?:Exception|Error))(?::.*)?$");
    private static final int MAX_FRAMES = 3;

    private StackTraceFingerprint() {}

    static String signature(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) return "";
        String deepestException = "";
        List<String> frames = new ArrayList<>();
        for (String line : rawLog.lines().toList()) {
            Matcher exception = EXCEPTION.matcher(line.trim());
            if (exception.find()) deepestException = exception.group(1).toLowerCase(Locale.ROOT);
            Matcher frame = FRAME.matcher(line);
            if (frame.matches() && frames.size() < MAX_FRAMES) {
                frames.add((frame.group(1) + "(" + frame.group(2) + ")").toLowerCase(Locale.ROOT));
            }
        }
        if (deepestException.isBlank() && frames.isEmpty()) return "";
        return deepestException + "|" + String.join(">", frames);
    }
}
