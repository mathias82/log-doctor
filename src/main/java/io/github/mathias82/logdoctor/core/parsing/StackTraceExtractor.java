package io.github.mathias82.logdoctor.core.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts exception blocks roughly like:
 *  java.lang.RuntimeException: msg
 *   at ...
 *  Caused by: ...
 */
public final class StackTraceExtractor {

    private static final Pattern TOP_EXCEPTION =
            Pattern.compile("(?m)^(?:Caused by:\\s*)?([a-zA-Z0-9_$.]+Exception|[a-zA-Z0-9_$.]+Error):\\s*(.*)$");

    private static final Pattern STACK_FRAME =
            Pattern.compile("(?m)^\\s*at\\s+(.+)$");

    public List<ExceptionSignature> extract(String content) {
        List<ExceptionSignature> out = new ArrayList<>();

        Matcher m = TOP_EXCEPTION.matcher(content);
        while (m.find()) {
            String ex = m.group(1);
            String msg = m.group(2);

            // get first 8 frames after this match (naive window)
            int start = m.end();
            String tail = content.substring(start, Math.min(content.length(), start + 4000));

            Matcher frames = STACK_FRAME.matcher(tail);
            List<String> collected = new ArrayList<>();
            while (frames.find() && collected.size() < 8) {
                collected.add(frames.group(1).trim());
            }

            out.add(new ExceptionSignature(ex, msg, collected));
        }

        return out;
    }
}
