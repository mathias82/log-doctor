package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.LogLine;

import java.util.List;

public class FailureContextExtractor {

    public String extract(List<LogLine> lines, FailureLocator.Failure failure, int radius) {

        int center = failure.rootCause().lineNumber() - 1;

        int from = Math.max(0, center - radius);
        int to = Math.min(lines.size(), center + radius + 1);

        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append(lines.get(i).lineNumber())
                    .append(": ")
                    .append(lines.get(i).content())
                    .append("\n");
        }

        return sb.toString();
    }
}
