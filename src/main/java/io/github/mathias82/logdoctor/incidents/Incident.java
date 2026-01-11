package io.github.mathias82.logdoctor.incidents;

import io.github.mathias82.logdoctor.core.analysis.Severity;

public interface Incident {

    String id();

    String title();

    String description();

    String rootCause();

    String recommendation();

    Severity severity();
}
