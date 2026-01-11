package io.github.mathias82.logdoctor.core;

public record LogLine(
        int lineNumber,
        String timestamp,
        String level,
        String content
) {}
