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

## Agent diagnostic contract

An agent-facing representation should expose existing backend-owned facts rather than inventing new diagnoses in a client adapter. The contract is intentionally provider-neutral:

```json
{
  "contractVersion": "1",
  "incident": {
    "diagnosis": "Kafka authentication failure",
    "severity": "HIGH",
    "confidence": "HIGH",
    "fingerprint": "..."
  },
  "evidence": [
    "..."
  ],
  "rootCauseCandidates": [
    "..."
  ],
  "investigation": {
    "inspect": ["..."],
    "changeCandidates": ["..."],
    "validate": ["..."],
    "escalateWhen": ["..."]
  },
  "safety": {
    "automaticExecutionAllowed": false
  }
}
```

Field names in an eventual API/CLI implementation should map to the existing diagnosis, evidence, fingerprinting, correlation and remediation models. The example above documents the boundary and is not a promise that every field is already emitted by the current CLI.

## Integration targets

The contract is deliberately not tied to one vendor. It can be used as context by Claude Code, GitHub coding agents, local agent frameworks, CI orchestration, or other tools that can consume JSON.

A future adapter may offer a dedicated `agent` output mode, but the deterministic engine must remain independently usable without an agent or LLM.

## Safety boundary

Agent integration must preserve these invariants:

- deterministic analysis runs before optional LLM enrichment;
- sensitive-data redaction happens before any LLM boundary;
- raw logs are not added to observability metrics;
- match confidence is evidence strength, not permission to execute a fix;
- `automaticExecutionAllowed=false` remains authoritative;
- proposed code or configuration changes require review outside Log Doctor.
