package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.LogLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the visible exception chain from a JVM stack trace without using an LLM.
 */
public final class CauseChainAnalyzer {

    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^(?:Caused by:\\s*)?([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*(?:Exception|Error))(?::\\s*(.*))?$"
    );

    public List<Cause> analyze(List<LogLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<Cause> causes = new ArrayList<>();
        for (LogLine line : lines) {
            String content = line.content() == null ? "" : line.content().trim();
            if (content.startsWith("Suppressed:")) {
                continue;
            }

            Matcher matcher = EXCEPTION_LINE.matcher(content);
            if (!matcher.matches()) {
                continue;
            }

            String exceptionType = matcher.group(1);
            String message = matcher.group(2) == null ? "" : matcher.group(2).trim();
            Cause cause = new Cause(line.lineNumber(), exceptionType, message, content);

            if (causes.isEmpty() || !sameCause(causes.get(causes.size() - 1), cause)) {
                causes.add(cause);
            }
        }

        return List.copyOf(causes);
    }

    private boolean sameCause(Cause left, Cause right) {
        return left.exceptionType().equals(right.exceptionType())
                && left.message().equals(right.message());
    }

    public record Cause(
            int lineNumber,
            String exceptionType,
            String message,
            String evidence
    ) {}
}
