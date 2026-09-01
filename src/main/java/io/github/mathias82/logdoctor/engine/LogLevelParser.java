package io.github.mathias82.logdoctor.engine;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a log level only when it appears in a structured log prefix.
 * Message text such as "cache returned INFO metadata" must not be interpreted
 * as the level of the log entry.
 */
final class LogLevelParser {

    private static final String LEVELS = "TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL";
    private static final String TIMESTAMP =
            "\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?";

    private static final Pattern LEADING_LEVEL = Pattern.compile(
            "(?i)^\\s*(" + LEVELS + ")(?=\\s|:|\\[|$)");
    private static final Pattern TIMESTAMPED_LEVEL = Pattern.compile(
            "(?i)^\\s*" + TIMESTAMP + "\\s+(?:\\[[^\\]\\r\\n]{1,120}\\]\\s+)*(" + LEVELS + ")(?=\\s|:|\\[|$)");

    private LogLevelParser() {}

    static String parse(String line) {
        if (line == null || line.isBlank()) return null;

        Matcher leading = LEADING_LEVEL.matcher(line);
        if (leading.find()) return normalize(leading.group(1));

        Matcher timestamped = TIMESTAMPED_LEVEL.matcher(line);
        return timestamped.find() ? normalize(timestamped.group(1)) : null;
    }

    private static String normalize(String level) {
        return level.toUpperCase(Locale.ROOT);
    }
}
