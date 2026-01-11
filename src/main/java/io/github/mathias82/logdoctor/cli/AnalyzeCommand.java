package io.github.mathias82.logdoctor.cli;

import io.github.mathias82.logdoctor.core.analysis.AnalysisEngine;
import io.github.mathias82.logdoctor.core.analysis.AnalysisResult;
import io.github.mathias82.logdoctor.core.analysis.Finding;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "analyze",
        description = "Analyze a log file and print the most likely root cause + fixes.",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, required = true, description = "Path to log file.")
    Path file;

    @Option(names = {"--max-findings"}, defaultValue = "5", description = "Max findings to print.")
    int maxFindings;

    @Override
    public Integer call() {
        AnalysisEngine engine = AnalysisEngine.defaultEngine();
        AnalysisResult result = engine.analyzeFile(file);

        if (result.findings().isEmpty()) {
            System.out.println("No obvious failure pattern found. Try providing more logs around the error.");
            return 0;
        }

        System.out.println("=== Log Doctor Report ===");
        System.out.println("File: " + file.toAbsolutePath());
        System.out.println();

        int count = 0;
        for (Finding f : result.findings()) {
            count++;
            System.out.printf("%d) [%s] %s%n", count, f.severity(), f.title());
            System.out.println("   Signature: " + f.signature());
            System.out.println("   Why: " + f.why());
            if (!f.howToFix().isEmpty()) {
                System.out.println("   Fix:");
                for (String step : f.howToFix()) {
                    System.out.println("    - " + step);
                }
            }
            System.out.println();
            if (count >= maxFindings) break;
        }

        return 0;
    }
}
