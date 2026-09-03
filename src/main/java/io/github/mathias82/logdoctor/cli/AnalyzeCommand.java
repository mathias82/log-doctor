package io.github.mathias82.logdoctor.cli;

import io.github.mathias82.logdoctor.engine.DiagnosisEngine;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AnalyzeCommand {

    private AnalyzeCommand() {}

    public static void run(String[] args) {
        if (args.length < 2 || !args[0].equals("--file")) {
            System.out.println("Usage: log-doctor --file <logfile> [--format text|json|github]");
            return;
        }

        try {
            Path source = Path.of(args[1]);
            String log = Files.readString(source);
            DiagnosisEngine.DiagnosisResult result = new DiagnosisEngine().analyzeStructured(log);
            System.out.println(format(result, source, resolveFormat(args)));
        } catch (Exception e) {
            System.err.println("Failed to analyze log: " + e.getMessage());
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

    private static String validFormat(String raw) {
        String format = raw == null ? "" : raw.trim().toLowerCase();
        return switch (format) {
            case "text", "json", "github" -> format;
            default -> throw new IllegalArgumentException("Unsupported format: " + raw);
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
