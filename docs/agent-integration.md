# Agent integration

Log Doctor is a deterministic diagnostic layer between production logs and humans, CI systems, or coding agents.

The intended workflow is:

```text
logs -> deterministic diagnosis + evidence -> developer or coding agent -> proposed change -> human review
```

Log Doctor does not authorize autonomous production changes. Existing remediation safety metadata remains authoritative and automatic execution stays disabled.

## Why put Log Doctor before an agent?

Coding agents are good at navigating a repository, reasoning across source files and proposing changes. Production-log diagnosis has a different set of requirements: repeated failures should be grouped consistently, known JVM/Spring/Kafka/DB failures should not need to be rediscovered probabilistically, sensitive values should be removed before an LLM boundary, and the evidence that caused a diagnosis should be auditable.

Log Doctor therefore owns deterministic incident extraction and diagnosis. An agent can consume the resulting structured evidence and use it as context for code investigation.

## CLI agent output

Use the provider-neutral JSON envelope with:

```bash
java -jar target/log-doctor-0.4.2.jar --file app.log --format agent
```

The `agent` format is separate from the full internal `json` representation. It exposes a deliberately scoped contract for agent consumers and redacts the evidence fields before serialization.

The current contract version is `1` and includes:

- source path and failure line when available;
- analysis status and whether local-LLM assistance was used;
- incident type, category, severity, confidence and match score;
- redacted evidence, match reasons, score factors and cause chain;
- root-cause candidates;
- backend-owned remediation/investigation metadata;
- explicit safety flags including `automaticExecutionAllowed` and `humanReviewRequired`.

A representative envelope is:

```json
{
  "contractVersion": "1",
  "producer": {
    "name": "Log Doctor",
    "output": "agent"
  },
  "source": {
    "path": "app.log",
    "failureLine": 42
  },
  "analysis": {
    "status": "DIAGNOSED",
    "source": "DETERMINISTIC_OR_FALLBACK",
    "llmUsed": false
  },
  "incident": {
    "type": "KAFKA_AUTHORIZATION",
    "category": "SECURITY",
    "severity": "HIGH",
    "confidence": "HIGH",
    "summary": "...",
    "location": "...",
    "matchScore": 95,
    "matchConfidence": "HIGH"
  },
  "evidence": {
    "excerpt": "...",
    "whyMatched": ["..."],
    "matchScoreFactors": ["..."],
    "causeChain": []
  },
  "rootCauseCandidates": ["..."],
  "investigation": {
    "allowedActions": ["..."],
    "verificationSteps": ["..."],
    "playbook": {}
  },
  "safety": {
    "automaticExecutionAllowed": false,
    "humanReviewRequired": true,
    "redactionAppliedToAgentEvidence": true
  }
}
```

The adapter projects backend-owned facts; it does not create a second diagnosis policy in the CLI.

## Integration targets

The contract is deliberately not tied to one vendor. It can be used as context by Claude Code, GitHub coding agents, local agent frameworks, CI orchestration, or other tools that can consume JSON.

For example, a workflow can save `--format agent` output as an artifact or pipe it into an agent orchestration step. The deterministic engine remains independently useful without an agent or LLM.

## Safety boundary

Agent integration preserves these invariants:

- deterministic analysis runs before optional LLM enrichment;
- sensitive-data redaction happens before any LLM boundary;
- agent-facing evidence is redacted again before it leaves the CLI formatter;
- raw logs are not added to observability metrics;
- match confidence is evidence strength, not permission to execute a fix;
- `automaticExecutionAllowed=false` remains authoritative;
- proposed code or configuration changes require review outside Log Doctor.
