# Public contract compatibility

Log Doctor keeps a machine-readable inventory of compatibility-sensitive public surfaces in
`src/test/resources/compatibility/public-contract-v1.json`. The focused gate verifies that every
required contract remains available while allowing new fields, metrics and Action inputs to be
added without rewriting the snapshot.

Run the same entry point used by CI:

```bash
./scripts/check-public-contracts.sh
```

The check writes `target/public-contract-compatibility.json`. A failure names the affected surface
and the missing or changed member, field, value or behavior.

## Protected surfaces

| Surface | Compatibility commitments |
|---|---|
| HTTP API | Version header/value, health payload, required analyze and batch fields, grouping metadata and redaction-report fields |
| CLI | Format names, `--fail-on` values and semantics, defaults, and exit codes `0`, `2` and `3` |
| GitHub Action | Required inputs, defaults, output wiring and CLI failure-policy forwarding |
| SARIF | SARIF 2.1.0, stable representative `LOGDOCTOR-*` rule identifiers and LOW/MEDIUM/HIGH/CRITICAL level mapping |
| Remediation | Metadata/playbook fields, manual-only `NO_AUTOMATIC_FIX` behavior and `automaticExecutionAllowed=false` |
| Observability | Published Prometheus sample names; additional metric names remain allowed |
| Rule-provider SPI | Public interface signatures, built-in/extension/catalog ordering, first-match behavior and fail-soft isolation |
| Java API | Required public constructors, methods, record components and canonical DTO constructors |

The gate intentionally does not freeze implementation-only classes, private helpers, wording inside
diagnosis prose or the complete set of future additive response fields.

## Intentional changes

1. Run the gate and identify every affected surface before changing the snapshot.
2. Prefer an additive change when existing consumers can continue without modification. New HTTP
   fields, Action inputs or Prometheus metrics do not require deleting existing snapshot entries.
3. For a deliberate breaking Java, CLI, Action, SARIF, remediation, metric or SPI change, document
   the migration and compatibility impact in the release notes, update the implementation and
   focused tests, then update the snapshot in the same reviewed pull request.
4. Removing or renaming an HTTP response field, changing its type or meaning, or restructuring an
   endpoint requires an HTTP API contract-version bump. Update `LogDoctorWebServer.API_VERSION`,
   `docs/api-contract.md`, the machine-readable snapshot and integration guidance together.
5. Changes to `NO_AUTOMATIC_FIX`, human-review requirements or
   `automaticExecutionAllowed=false` require an explicit safety review; updating the snapshot alone
   is not approval to enable execution.

The HTTP contract version is independent from the Maven artifact version. Compatible feature and
patch releases may continue to use the current HTTP contract version.
