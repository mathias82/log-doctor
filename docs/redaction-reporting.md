# Redaction reporting

Log Doctor performs deterministic best-effort redaction before any LLM boundary. The Web/API layer also exposes a per-analysis redaction report so users can see whether sensitive-data patterns were detected without revealing the matched values.

## Response shape

`POST /api/analyze` and `POST /api/analyze/batch` include an additive top-level field:

```json
{
  "redactionReport": {
    "totalRedactions": 3,
    "sensitiveDataDetected": true,
    "categories": {
      "SECRET_ASSIGNMENT": 1,
      "EMAIL": 1,
      "IPV4": 1
    },
    "appliedBeforeLlm": true,
    "valuesExposed": false
  }
}
```

The report contains counts only. It never stores or returns the matched secret, token, email address or IP value.

## Categories

The current deterministic categories are:

- `BEARER_TOKEN`
- `JWT`
- `SECRET_ASSIGNMENT`
- `QUERY_SECRET`
- `EMAIL`
- `IPV4`

A value is counted in the first applicable redaction stage. Counts are intended as an auditable safety signal, not as a complete data-loss-prevention classification system.

## Semantics

`appliedBeforeLlm=true` means the same redaction policy is applied before Log Doctor sends prompt content to the configured Ollama endpoint. The report is calculated for every analysis, even when no LLM call is needed, so the user can see what would be removed at the boundary.

`valuesExposed=false` is a contract statement about the report itself: only category names and counts are exposed.

## Limitations

Redaction is defense-in-depth. Applications should avoid logging credentials, tokens, personal data and other secrets in the first place. Pattern-based redaction cannot guarantee discovery of every sensitive value or organization-specific identifier.

The report deliberately does not include samples, hashes or reversible identifiers for matches. This avoids turning the safety report into another sensitive-data surface.
