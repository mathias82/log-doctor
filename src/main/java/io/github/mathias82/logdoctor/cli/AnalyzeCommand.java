package io.github.mathias82.logdoctor.cli;

import io.github.mathias82.logdoctor.engine.DiagnosisEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AnalyzeCommand {

    static final int EXIT_OK = 0;
    static final int EXIT_POLICY_MATCHED = 2;
    static final int EXIT_USAGE_OR_ANALYSIS_ERROR = 3;

    private AnalyzeCommand() {}

    public static int run(String[] args) {
        if (args.length < 2 || !args[0].equals("--file")) {
            System.out.println("Usage: log-doctor --file <logfile> [--format text|json|github] [--fail-on none|diagnosis|high|critical]");
            return EXIT_USAGE_OR_ANALYSIS_ERROR;
        }

        try {
            Path source = Path.of(args[1]);
            String log = Files.readString(source);
            DiagnosisEngine.DiagnosisResult result = new DiagnosisEngine().analyzeStructured(log);
            String format = resolveFormat(args);
            String failOn = resolveFailOn(args);
            System.out.println(format(result, source, format));
            return shouldFail(result, failOn) ? EXIT_POLICY_MATCHED : EXIT_OK;
        } catch (Exception e) {
            System.err.println("Failed to analyze log: " + e.getMessage());
            return EXIT_USAGE_OR_ANALYSIS_ERROR;
        }
    }

    static String resolveFormat(String[] args) {
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--format=")) {
                return validFormat(arg.substring("--format=".length()));
            }
            if ("--format".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--format requires a value");
                }
                return validFormat(args[i + 1]);
            }
        }
        return "text";
    }

    static String resolveFailOn(String[] args) {
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--fail-on=")) {
                return validFailOn(arg.substring("--fail-on=".length()));
            }
            if ("--fail-on".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--fail-on requires a value");
                }
                return validFailOn(args[i + 1]);
            }
        }
        return "none";
    }

    static boolean shouldFail(DiagnosisEngine.DiagnosisResult result, String failOn) {
        if (result == null || "NO_FAILURE".equals(result.status()) || "none".equals(failOn)) {
            return false;
        }
        if ("diagnosis".equals(failOn)) {
            return true;
        }

        int actualSeverity = severityRank(result.severity());
        int threshold = "critical".equals(failOn) ? severityRank("CRITICAL") : severityRank("HIGH");
        return actualSeverity >= threshold;
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase(Locale.ROOT)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    private static String validFormat(String raw) {
        String format = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (format) {
            case "text", "json", "github" -> format;
            default -> throw new IllegalArgumentException("Unsupported format: " + raw);
        };
    }

    private static String validFailOn(String raw) {
        String failOn = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (failOn) {
            case "none", "diagnosis", "high", "critical" -> failOn;
            default -> throw new IllegalArgumentException("Unsupported --fail-on value: " + raw);
        };
    }

    private static String format(DiagnosisEngine.DiagnosisResult result, Path source, String format) {
        return switch (format) {
            case "json" -> CiOutputFormatter.json(result);
            case "github" -> CiOutputFormatter.github(result, source);
            default -> result.diagnosis();
        };
    }
}
