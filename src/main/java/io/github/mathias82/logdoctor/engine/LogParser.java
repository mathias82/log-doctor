package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.LogLine;

import java.util.ArrayList;
import java.util.List;

public class LogParser {

    public List<LogLine> parse(String rawLog) {
        List<LogLine> lines = new ArrayList<>();

        String[] split = rawLog.split("\\R");

        for (int i = 0; i < split.length; i++) {
            String line = split[i];

            ParsedLine parsed = parseLine(line);

            lines.add(new LogLine(
                    i + 1,
                    null,        // timestamp
                    null,        // level
                    parsed.content
            ));
        }

        return lines;
    }

    private ParsedLine parseLine(String line) {
        // πολύ απλό heuristic (αρκετό για αρχή)
        if (line.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            String[] parts = line.split(" ", 4);
            String timestamp = parts.length > 1 ? parts[0] + " " + parts[1] : null;
            String level = parts.length > 2 ? parts[2] : null;
            String content = parts.length > 3 ? parts[3] : line;
            return new ParsedLine(timestamp, level, content);
        }
        return new ParsedLine(null, null, line);
    }

    private record ParsedLine(String timestamp, String level, String content) {}
}
