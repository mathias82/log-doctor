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

## Failure policies and exit codes

CI jobs can opt into deterministic enforcement with `--fail-on`:

```bash
java -jar target/log-doctor-0.4.2.jar --file application.log --format github --fail-on high
```

Supported policies are:

- `none` — always succeed after a valid analysis; default behavior
- `diagnosis` — fail when any diagnosis or unknown failure is detected
- `high` — fail on `HIGH` or `CRITICAL` severity
- `critical` — fail only on `CRITICAL` severity

CLI exit codes are stable for CI use:

- `0` — analysis completed and the selected policy did not trigger
- `2` — analysis completed and the selected failure policy triggered
- `3` — usage, input or analysis error

A no-failure result never triggers a policy failure.

## Composite GitHub Action

The repository now includes `action.yml`, so a workflow can invoke Log Doctor directly and receive native annotations plus policy enforcement.

```yaml
- name: Diagnose application log
  uses: mathias82/log-doctor@main
  with:
    log-file: build/logs/application.log
    fail-on: high
```

The action sets up Java 21, builds the checked-out Log Doctor action source, analyzes the requested log in GitHub annotation mode, and exposes the CLI result as the `exit-code` output. For reproducible production workflows, pin the action to a release tag or commit SHA rather than `main`.

The repository also runs an `action-smoke` workflow that verifies both annotation-only success and severity-policy failure behavior.

## Safety boundary

These formats and the GitHub Action report diagnoses only. They do not execute remediation actions and do not change the `NO_AUTOMATIC_FIX` policy.
