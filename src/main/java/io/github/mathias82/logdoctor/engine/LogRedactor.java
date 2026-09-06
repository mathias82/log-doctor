package io.github.mathias82.logdoctor.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
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
        return redactWithReport(text).redactedText();
    }

    public RedactionResult redactWithReport(String text) {
        if (text == null || text.isEmpty()) {
            return new RedactionResult(text, RedactionReport.empty());
        }

        MutableCounts counts = new MutableCounts();
        String redacted = replaceAndCount(text, BEARER, "$1" + REDACTED, counts::bearer);
        redacted = replaceAndCount(redacted, JWT, REDACTED, counts::jwt);
        redacted = replaceAndCount(redacted, SECRET_ASSIGNMENT, "$1$2" + REDACTED + "$4", counts::secretAssignment);
        redacted = replaceAndCount(redacted, QUERY_SECRET, "$1" + REDACTED, counts::querySecret);
        redacted = replaceAndCount(redacted, EMAIL, "<redacted-email>", counts::email);
        redacted = replaceAndCount(redacted, IPV4, "<redacted-ip>", counts::ipv4);
        return new RedactionResult(redacted, counts.report());
    }

    private static String replaceAndCount(String input, Pattern pattern, String replacement, Counter counter) {
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) count++;
        if (count == 0) return input;
        counter.add(count);
        return pattern.matcher(input).replaceAll(replacement);
    }

    public record RedactionResult(String redactedText, RedactionReport report) {}

    public record RedactionReport(int totalRedactions, Map<String, Integer> categories) {
        public static RedactionReport empty() {
            return new RedactionReport(0, Map.of());
        }

        public boolean sensitiveDataDetected() {
            return totalRedactions > 0;
        }
    }

    @FunctionalInterface
    private interface Counter {
        void add(int amount);
    }

    private static final class MutableCounts {
        private int bearerTokens;
        private int jwtTokens;
        private int secretAssignments;
        private int querySecrets;
        private int emailAddresses;
        private int ipv4Addresses;

        void bearer(int amount) { bearerTokens += amount; }
        void jwt(int amount) { jwtTokens += amount; }
        void secretAssignment(int amount) { secretAssignments += amount; }
        void querySecret(int amount) { querySecrets += amount; }
        void email(int amount) { emailAddresses += amount; }
        void ipv4(int amount) { ipv4Addresses += amount; }

        RedactionReport report() {
            Map<String, Integer> categories = new LinkedHashMap<>();
            putIfPositive(categories, "BEARER_TOKEN", bearerTokens);
            putIfPositive(categories, "JWT", jwtTokens);
            putIfPositive(categories, "SECRET_ASSIGNMENT", secretAssignments);
            putIfPositive(categories, "QUERY_SECRET", querySecrets);
            putIfPositive(categories, "EMAIL", emailAddresses);
            putIfPositive(categories, "IPV4", ipv4Addresses);
            int total = categories.values().stream().mapToInt(Integer::intValue).sum();
            return new RedactionReport(total, Map.copyOf(categories));
        }

        private static void putIfPositive(Map<String, Integer> target, String category, int count) {
            if (count > 0) target.put(category, count);
        }
    }
}
