# Runtime privacy status

Log Doctor exposes a runtime privacy boundary at:

```text
GET /api/privacy
```

The endpoint reports whether deterministic analysis is local, whether the configured Ollama endpoint is on the same machine or remote, whether redaction is applied before the LLM boundary, whether raw logs are included in metrics, and whether automatic remediation is enabled.

Example for the default local configuration:

```json
{
  "analysisProcess": "LOCAL",
  "deterministicAnalysis": "LOCAL",
  "llmProvider": "OLLAMA",
  "llmEndpointScope": "LOCAL_MACHINE",
  "logsLeaveMachineForConfiguredLlm": false,
  "redactionBeforeLlm": true,
  "rawLogsInMetrics": false,
  "automaticRemediation": false
}
```

If `LOG_DOCTOR_OLLAMA_URL` or `log.doctor.ollama.url` points to a non-loopback host, the endpoint reports `REMOTE_CONFIGURED` and `logsLeaveMachineForConfiguredLlm=true`.

The Web UI consumes this endpoint and displays the effective runtime boundary. This avoids making a blanket "local-only" claim when an operator has deliberately configured a remote Ollama endpoint.

## Security interpretation

This status is descriptive, not a security certification. It does not claim that an open-source package is inherently safer than a cloud service. It makes the active trust boundary inspectable:

- deterministic diagnosis executes in the Log Doctor process;
- sensitive-data redaction is applied before an LLM request;
- raw logs, prompts and LLM responses are not exported as runtime metrics;
- automatic remediation remains disabled;
- a remote configured Ollama endpoint is reported as remote instead of being labelled local.

Redaction remains defense-in-depth. Applications should avoid writing credentials, tokens, customer data or other secrets into logs in the first place.
