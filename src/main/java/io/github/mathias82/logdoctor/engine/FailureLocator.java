package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.LogLine;
import java.util.List;
import java.util.Optional;

public class FailureLocator {

    public Optional<Failure> locate(List<LogLine> lines) {

        List<LogLine> causedByLines = lines.stream()
                .filter(l -> l.content().trim().startsWith("Caused by"))
                .toList();

        LogLine rootCause;

        if (!causedByLines.isEmpty()) {
            rootCause = causedByLines.get(causedByLines.size() - 1);
        } else {
            rootCause = lines.stream()
                    .filter(l -> l.content().contains("Exception")
                            || l.content().contains("Error"))
                    .reduce((a, b) -> b)
                    .orElse(null);
        }

        if (rootCause == null) {
            return Optional.empty();
        }

        LogLine blame = findApplicationFrame(lines, rootCause)
                .orElse(rootCause);

        return Optional.of(new Failure(rootCause, blame));
    }

    /**
     * Βρίσκει ΠΟΥ στον κώδικα έγινε το λάθος
     */
    private Optional<LogLine> findApplicationFrame(
            List<LogLine> lines, LogLine cause) {

        for (int i = cause.lineNumber() - 2; i >= 0; i--) {
            String c = lines.get(i).content();
            if (c.contains("at com.")
                    && !c.contains(".domain.")
                    && !c.contains("Entity")) {
                return Optional.of(lines.get(i));
            }
        }

        for (int i = cause.lineNumber() - 1; i >= 0; i--) {
            String c = lines.get(i).content();
            if (c.contains(".service.")
                    && (c.contains("INFO")
                    || c.contains("DEBUG")
                    || c.contains("WARN")
                    || c.contains("ERROR"))
            ) {
                return Optional.of(lines.get(i));
            }
        }

        return Optional.empty();
    }


    public record Failure(
            LogLine rootCause,
            LogLine blameLocation
    ) {}
}
