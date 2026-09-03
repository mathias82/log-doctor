# SARIF and GitHub Code Scanning

Log Doctor can emit SARIF 2.1.0 so deterministic log findings can be uploaded to GitHub Code Scanning.

```bash
java -jar target/log-doctor-*.jar --file logs/app.log --format sarif > log-doctor.sarif
```

The SARIF report contains:

- a stable `LOGDOCTOR-*` rule identifier derived from the diagnosed incident type;
- severity mapped to SARIF `error`, `warning`, or `note`;
- the analyzed log path and detected failure line when available;
- summary and root-cause text;
- diagnostic category, confidence, fix policy, and safety metadata.

A healthy log produces a valid SARIF document with an empty `results` array.

## GitHub Code Scanning

A workflow can generate the report and upload it with `github/codeql-action/upload-sarif`:

```yaml
permissions:
  contents: read
  security-events: write

steps:
  - uses: actions/checkout@v5
  - name: Analyze log
    run: java -jar log-doctor.jar --file logs/app.log --format sarif > log-doctor.sarif
  - name: Upload Log Doctor findings
    uses: github/codeql-action/upload-sarif@v3
    with:
      sarif_file: log-doctor.sarif
      category: log-doctor
```

SARIF is reporting only. It does not execute remediation and does not change Log Doctor's `NO_AUTOMATIC_FIX` safety policy.

The repository includes `.github/workflows/sarif-smoke.yml` to validate generation and Code Scanning upload on pull requests.
