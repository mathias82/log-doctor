# Contributing unknown production failures

Log Doctor can turn an unknown or human-review incident into a draft GitHub issue from the Web UI.

The goal is to shorten the path from a real failure that the deterministic engine does not understand to a reviewed regression case and deterministic rule.

## Flow

```text
unknown incident
  -> Log Doctor redacted evidence
  -> pre-filled GitHub issue
  -> contributor reviews content
  -> contributor explicitly submits
  -> regression case + deterministic rule
```

The browser never submits an issue automatically. The button opens GitHub's normal new-issue screen with a pre-filled title and body so the contributor can inspect, edit, or abandon it.

## Data included

The draft contains only diagnostic metadata already present in the analysis response and redacted evidence. It includes the number of redactions detected, but not the matched values. Raw input logs are not copied into the draft.

Contributors are explicitly asked to review the draft and remove organization-specific or sensitive information before publication.

## Maintainer workflow

A useful unknown-failure issue should become:

1. a minimal sanitized reproduction or labelled regression case;
2. a deterministic rule when the signal is stable enough;
3. positive tests for the expected diagnosis;
4. negative/near-miss tests to protect precision;
5. documentation when the new failure class is broadly useful.

Do not add a broad pattern merely to make one submitted example pass. Prefer evidence that distinguishes the failure from plausible near misses.
