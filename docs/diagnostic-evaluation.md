# Independent diagnostic evaluation

Log Doctor keeps broader evaluation evidence separate from its curated regression quality gates.

The independent evaluation dataset lives at `src/test/resources/diagnostic-evaluation/corpus.json`. It contains publication-safe scenarios that are not copied from the 120-case regression corpus. The dataset includes realistic positives, hard negatives, ambiguous references and cross-subsystem lookalikes across JVM, Spring, Kafka and database diagnostics.

## Dataset contract

The top-level dataset records:

- a stable dataset id, schema version and dataset version;
- provenance and the labelling method;
- whether raw production logs were used;
- whether detector output was used to derive labels;
- known limitations;
- independently stored expected outcomes and label rationales for every case.

Only synthetic or explicitly sanitized, publication-safe examples are allowed. The validation test rejects common credential patterns, duplicate ids, exact copies of regression fixtures and cases without a rationale. Raw customer or user logs must not be added.

Labels are inputs to evaluation, not outputs from the detector. A maintainer should review the expected outcome and rationale on dataset changes without rewriting labels merely to match current implementation behavior.

## Case kinds

- `POSITIVE`: a supported deterministic diagnosis is expected.
- `HARD_NEGATIVE`: operationally plausible text that should not be diagnosed.
- `AMBIGUOUS`: a diagnostic term appears without evidence that the incident occurred.
- `CROSS_SUBSYSTEM_LOOKALIKE`: a similar failure belongs to another runtime or subsystem.

Every JVM, Spring, Kafka and DB category contains all four kinds.

## Output

`IndependentDiagnosticEvaluationTest` writes `target/diagnostic-evaluation.json` during normal Maven verification. The report contains:

- aggregate and per-category confusion counts;
- precision, recall, false-positive rate and exact-rule accuracy;
- counts by case kind;
- explicit mismatch records with expected and actual outcomes;
- dataset provenance and interpretation notes.

The same corpus is evaluated twice in the test to protect deterministic output.

## CI and interpretation

CI publishes the JSON report in the job summary and as the `diagnostic-evaluation` artifact. The existing `target/diagnostic-benchmark.json` artifact and all regression thresholds remain unchanged.

The independent evaluation is initially evidence, not a release gate. It is deliberately broader and more ambiguous than the curated regression suite, so mismatches remain visible instead of being hidden by weakening labels or matching thresholds. The results do not estimate production-wide statistical accuracy.

