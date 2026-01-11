package io.github.mathias82.logdoctor.core.analysis;

import java.util.List;

public record Finding(
        Severity severity,
        String title,
        String signature,
        String why,
        List<String> howToFix
) { }
