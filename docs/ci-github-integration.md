# CI and GitHub integration

The CLI supports machine-friendly output without changing the default human-readable diagnosis.

```bash
java -jar target/log-doctor-0.4.2.jar --file app.log --format text
java -jar target/log-doctor-0.4.2.jar --file app.log --format json
java -jar target/log-doctor-0.4.2.jar --file app.log --format github
```

## JSON

`--format json` prints the complete structured `DiagnosisResult`, including status, type, category, match evidence, remediation metadata and LLM provenance. This is suitable for shell pipelines, CI jobs and downstream automation that needs the same contract as the single-diagnosis HTTP API.

## GitHub Actions annotations

`--format github` emits a GitHub workflow command annotation. The source log path is included as the annotation file and the detected failure line is included when available. High-severity diagnoses become `error` annotations; other diagnoses become `warning` annotations; no-failure results become `notice` annotations.

Example:

```bash
java -jar target/log-doctor-0.4.2.jar --file application.log --format github
```

When executed inside GitHub Actions, the output is rendered directly in the workflow UI. Annotation properties and messages are escaped before emission so log-derived newlines and delimiters cannot corrupt the workflow-command format.

## Safety boundary

These formats report diagnoses only. They do not execute remediation actions and do not change the `NO_AUTOMATIC_FIX` policy.
