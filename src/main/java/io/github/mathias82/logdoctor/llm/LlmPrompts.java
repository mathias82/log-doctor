package io.github.mathias82.logdoctor.llm;

import io.github.mathias82.logdoctor.core.*;

import java.util.stream.Collectors;

public final class LlmPrompts {

    public LlmPrompts(){}

    public static String knownIncidentPrompt(Incident incident) {

        var allowedFixes = FixPolicy.allowedFixes(incident.category())
                .stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));

        return """
    You are a senior JVM / Spring / Kafka production engineer.

    This incident was detected with HIGH CONFIDENCE.
    Do NOT explain theory. Do NOT summarize logs.

    Incident:
    Type: %s
    Category: %s
    Severity: %s

    Root cause:
    %s

    Log evidence:
    %s

    ALLOWED FIX TYPES:
    %s

    STRICT DOMAIN RULES:

    - For DATABASE incidents:
      - XML-based Spring configuration is FORBIDDEN unless explicitly present in logs
      - The fix MUST be in the SERVICE layer
      - The fix MUST define a transaction boundary
      - The fix MUST NOT use:
        * EntityManager.unwrap
        * Session / openSession
        * Open Session In View
        * Manual lazy initialization
      - The fix MUST be one of:
        * @Transactional on service method
        * Repository query with JOIN FETCH
        * DTO projection at repository level

    - Controller-level fixes are FORBIDDEN.
    - Hibernate Session APIs are FORBIDDEN.
    - You MUST NOT invent infrastructure or framework workarounds.

    YOUR TASK (ONLY THESE 2 THINGS):

    1) Explain EXACTLY where the error is:
       - component (class)
       - layer (controller / service / repository)
       - method or line if possible

    2) Provide the FIX using ONLY ONE allowed fix type.

    OUTPUT FORMAT (MANDATORY):

    WHERE:
    <short, precise explanation>

    FIX_TYPE: <one of %s>

    FIX:
    ```
    <fix here>
    ```

    """.formatted(
                incident.type(),
                incident.category(),
                incident.severity(),
                incident.rootCause(),
                incident.evidence,
                allowedFixes,
                allowedFixes
        );
    }


    public static String unknownLogPrompt(String rawLog) {
        return """
        You are a senior JVM / Spring / Kafka production engineer.

        Analyze the log below.
        TASK (ONLY THESE 2 THINGS):
                 
        1) WHERE:
           - Component (exact class if possible)
           - Layer (controller / service / repository / infra)
           - Reason (one short sentence)
         
        2) FIX:
           - Provide ONE concrete fix
           - Output ONLY code or config
           - Do NOT explain
           - Do NOT give alternatives
         
        If NO safe fix exists, output EXACTLY:
        No safe automatic fix – human investigation required.
                 

        Log:
        %s
        """.formatted(rawLog);
    }
}
