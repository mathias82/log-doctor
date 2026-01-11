package io.github.mathias82.logdoctor.core.analysis;

import io.github.mathias82.logdoctor.core.parsing.ExceptionSignature;
import io.github.mathias82.logdoctor.core.parsing.LogParser;
import io.github.mathias82.logdoctor.core.parsing.StackTraceExtractor;
import io.github.mathias82.logdoctor.core.rules.Rule;
import io.github.mathias82.logdoctor.core.rules.RuleCatalog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AnalysisEngine {
    private final LogParser parser;
    private final StackTraceExtractor extractor;
    private final List<Rule> rules;

    public AnalysisEngine(LogParser parser, StackTraceExtractor extractor, List<Rule> rules) {
        this.parser = parser;
        this.extractor = extractor;
        this.rules = List.copyOf(rules);
    }

    public static AnalysisEngine defaultEngine() {
        return new AnalysisEngine(
                new LogParser(),
                new StackTraceExtractor(),
                RuleCatalog.defaultRules()
        );
    }

    public AnalysisResult analyzeFile(Path file) {
        String content = parser.readAll(file);
        List<ExceptionSignature> signatures = extractor.extract(content);

        List<Finding> findings = new ArrayList<>();
        for (ExceptionSignature sig : signatures) {
            for (Rule rule : rules) {
                rule.match(sig).ifPresent(findings::add);
            }
        }

        // simple sort: highest severity first
        findings.sort((a, b) -> b.severity().compareTo(a.severity()));
        return new AnalysisResult(findings);
    }
}
