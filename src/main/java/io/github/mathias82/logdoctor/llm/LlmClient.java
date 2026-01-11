package io.github.mathias82.logdoctor.llm;

import io.github.mathias82.logdoctor.core.Incident;

public interface LlmClient {

    String explainKnownIncident(Incident incident);

    String analyzeUnknownLog(String rawLog);
}
