package io.github.mathias82.logdoctor.cli;

import io.github.mathias82.logdoctor.engine.DiagnosisEngine;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AnalyzeCommand {

    private AnalyzeCommand(){}

    public static void run(String[] args) {
        if (args.length < 2 || !args[0].equals("--file")) {
            System.out.println("Usage: log-doctor --file <logfile>");
            return;
        }

        try {
            String log = Files.readString(Path.of(args[1]));
            DiagnosisEngine engine = new DiagnosisEngine();
            engine.analyze(log);
        } catch (Exception e) {
            System.err.println("Failed to analyze log: " + e.getMessage());
        }
    }
}
