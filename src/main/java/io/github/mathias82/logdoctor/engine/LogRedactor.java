package io.github.mathias82.logdoctor.engine;

import java.util.regex.Pattern;

/**
 * Deterministic best-effort redaction for common secrets and identifiers found in logs.
 * This is intentionally conservative and dependency-free so it can run before any LLM call.
 */
public final class LogRedactor {
    private static final String REDACTED = "<redacted>";

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(authorization[\\\"']?\\s*[:=]\\s*[\\\"']?bearer\\s+)[^\\s,;\\\"']+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)([\\\"']?(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret)[\\\"']?\\s*[:=]\\s*)([\\\"']?)([^\\s,;\\\"'}]+)([\\\"']?)");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:token|access_token|api_key|key|secret|password)=)[^&#\\s]+");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?![\\d.])");

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String redacted = BEARER.matcher(text).replaceAll("$1" + REDACTED);
        redacted = JWT.matcher(redacted).replaceAll(REDACTED);
        redacted = SECRET_ASSIGNMENT.matcher(redacted).replaceAll("$1$2" + REDACTED + "$4");
        redacted = QUERY_SECRET.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = EMAIL.matcher(redacted).replaceAll("<redacted-email>");
        return IPV4.matcher(redacted).replaceAll("<redacted-ip>");
    }
}
