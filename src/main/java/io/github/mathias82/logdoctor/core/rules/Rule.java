package io.github.mathias82.logdoctor.core.rules;

import io.github.mathias82.logdoctor.core.analysis.Finding;
import io.github.mathias82.logdoctor.core.parsing.ExceptionSignature;

import java.util.Optional;

public interface Rule {
    Optional<Finding> match(ExceptionSignature sig);
}
