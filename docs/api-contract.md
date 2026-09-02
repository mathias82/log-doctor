# HTTP API compatibility contract

Log Doctor exposes a lightweight version signal for HTTP integrations without wrapping or renaming existing JSON payloads.

## Current contract version

Current API contract version: `1`.

Every HTTP response includes:

```text
X-Log-Doctor-Api-Version: 1
```

`GET /api/health` also returns the version in its JSON payload:

```json
{
  "status": "UP",
  "apiVersion": "1"
}
```

## Compatibility policy

Within one API contract version, Log Doctor should prefer additive changes. Existing fields should keep their meaning and type, and consumers should tolerate additional fields.

A new API contract version is required for changes such as removing or renaming an existing response field, changing the type or semantics of an existing field, or restructuring an endpoint response in a way that requires consumer changes.

The version is intentionally independent from the Maven artifact version. A patch or feature release can keep API contract version `1` when the HTTP contract remains backward compatible.

## Integration guidance

CI/CD jobs, Slack/Jira/GitHub adapters and monitoring integrations can record or validate `X-Log-Doctor-Api-Version` before processing a response. The existing `/api/analyze` and `/api/analyze/batch` JSON shapes remain unchanged by this versioning mechanism.

This version marker does not grant remediation execution permission. Remediation safety remains controlled by the diagnosis payload, including `NO_AUTOMATIC_FIX` and `automaticExecutionAllowed`.
