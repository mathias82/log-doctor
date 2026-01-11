package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.LogLine;

import java.util.List;

public record RuleContext(
        List<LogLine> lines,
        FailureLocator.Failure failure,
        String contextText
) {}
