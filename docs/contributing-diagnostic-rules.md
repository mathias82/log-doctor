# Contributing Deterministic Diagnostic Rules

This guide explains how to add a built-in deterministic diagnostic rule to Log Doctor safely and correctly.

It is intended for Java/Spring developers who are new to the Log Doctor codebase. Follow this guide to move from a synthetic or sanitized log pattern to a deterministic rule, tests, regression coverage, and a pull request.

## Contribution flow

```text
unknown incident
    ↓
redacted and reviewed evidence
    ↓
synthetic/sanitized regression case
    ↓
deterministic rule
    ↓
positive and near-miss tests
    ↓
local validation
    ↓
pull request
```

The goal is correctness and precision. Do not broaden a pattern simply to make one example match. A deterministic rule should use evidence that distinguishes the intended failure from plausible near misses.

## Safety boundary

Diagnostic rules identify and describe incidents. They do not execute fixes.

Keep the existing remediation safety boundary unchanged:

* do not enable automatic remediation or execution;
* do not change remediation behavior as part of a rule contribution;
* `NO_AUTOMATIC_FIX` and human-review semantics remain authoritative;
* `automaticExecutionAllowed=false` remains unchanged.

Use synthetic or sanitized examples only. Do not add real production logs, credentials, customer data, or other sensitive information.

## Where deterministic rules live

A built-in deterministic diagnosis usually involves the following locations:

```text
src/main/java/io/github/mathias82/logdoctor/incidents/
    Diagnosis information returned to the user.

src/main/java/io/github/mathias82/logdoctor/rules/
    Deterministic matching logic.

src/main/java/io/github/mathias82/logdoctor/engine/IncidentDetector.java
    Built-in rule registration and precedence.

src/test/java/io/github/mathias82/logdoctor/rules/
    Focused rule tests.
```

The regression corpus is located at:

```text
src/test/resources/diagnostic-benchmark/corpus.json
```

Start by checking whether an existing `Incident` and `IncidentRule` already cover the failure. Do not create a duplicate diagnosis when an existing specialized rule can safely represent it.

## How matching works

A deterministic rule implements `IncidentRule` and receives a `RuleContext`.

Conceptually, matching works like this:

```text
log context
    ↓
IncidentRule.match(context)
    ↓
Does the rule have sufficient deterministic evidence?
    ├── yes → return Optional.of(Incident)
    └── no  → return Optional.empty()
```

For example, a simple rule can check for a specific exception signal:

```java
public class ExampleFailureRule implements IncidentRule {
    @Override
    public Optional<Incident> match(RuleContext ctx) {
        if (ctx.contextText().contains("ExampleFailureException")) {
            ExampleFailureIncident incident = new ExampleFailureIncident();
            incident.setEvidence(ctx.contextText());
            return Optional.of(incident);
        }
        return Optional.empty();
    }
}
```

Keep matchers specific. If a signal is shared by multiple systems, require additional subsystem or context evidence instead of matching the exception name alone.

## Rule precedence

Built-in rules are evaluated in order by `IncidentDetector`. The first matching rule wins.

Log Doctor's broad `CommonFailureCatalogRule` runs after specialized built-in rules. This means a new specialized rule should be precise enough that it does not incorrectly match unrelated logs or steal traffic from a more appropriate diagnosis.

When changing or adding a rule, consider whether another rule could match the same log and add regression coverage when precedence matters.

## Structured rule contribution template

Use this template when adding a new deterministic diagnosis.

### 1. Define the diagnostic signal

Write down:

* the failure or exception being diagnosed;
* the exact evidence that should cause a match;
* evidence that distinguishes it from similar failures;
* examples that must not match.

Before writing the rule, answer:

```text
What exact log signal identifies this failure?

Could another subsystem produce the same exception name?

What additional evidence makes this diagnosis specific?

What realistic log should be a near miss and remain unmatched?
```

### 2. Create or reuse an incident

Incident classes live in:

```text
src/main/java/io/github/mathias82/logdoctor/incidents/
```

The incident describes the diagnosis returned by Log Doctor, including information such as:

* incident type;
* category;
* severity;
* confidence;
* summary;
* root cause;
* recommendation.

Reuse an existing incident when it already represents the diagnosis. Otherwise, add a focused new incident.

### 3. Implement the deterministic rule

Rules live in:

```text
src/main/java/io/github/mathias82/logdoctor/rules/
```

A rule should:

1. inspect `RuleContext`;
2. require sufficient deterministic evidence;
3. create or reuse the appropriate `Incident`;
4. attach matching evidence;
5. return `Optional.of(incident)` when matched;
6. return `Optional.empty()` when the evidence is insufficient.

Template:

```java
public class ExampleFailureRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        String text = ctx.contextText();

        if (!text.contains("ExampleFailureException")) {
            return Optional.empty();
        }

        ExampleFailureIncident incident = new ExampleFailureIncident();
        incident.setEvidence(text);

        return Optional.of(incident);
    }
}
```

Do not broaden a matcher merely to make one positive example pass. Prefer a specific signal and add additional context checks when the exception name or message is shared by multiple systems.

### 4. Register the specialized rule

Built-in specialized rules are registered in:

```text
src/main/java/io/github/mathias82/logdoctor/engine/IncidentDetector.java
```

Add the rule to `SPECIALIZED_RULES` in an appropriate position.

Rule order matters because the first matching rule wins. Consider whether another specialized rule or the broad `CommonFailureCatalogRule` could also match the same log.

### 5. Add focused tests

Rule tests live in:

```text
src/test/java/io/github/mathias82/logdoctor/rules/
```

Every new rule should include:

* at least one positive case that must match;
* at least one negative or near-miss case that must not match;
* an assertion about the expected diagnosis.

A near miss is especially important when the rule matches generic exception names, framework-level messages, HTTP codes, or other signals that could occur in unrelated systems.

### 6. Add regression corpus coverage where appropriate

The labelled diagnostic corpus is located at:

```text
src/test/resources/diagnostic-benchmark/corpus.json
```

Positive cases use:

```json
{
  "name": "example-failure-positive",
  "category": "JVM",
  "positive": true,
  "expectedRule": "ExampleFailureRule",
  "log": "synthetic failure log"
}
```

Negative or near-miss cases use:

```json
{
  "name": "example-failure-near-miss",
  "category": "JVM",
  "positive": false,
  "expectedRule": null,
  "log": "synthetic log that looks similar but should not match"
}
```

The `expectedRule` value for a positive case must match the rule name reported by `IncidentDetector`.

Keep corpus examples synthetic or sanitized. Do not add raw production logs or sensitive information.

### 7. Validate before opening a pull request

Run the focused tests and full validation commands described later in this guide.

Check that:

* the intended positive example matches;
* near misses remain unmatched;
* the expected specialized rule wins when precedence matters;
* the diagnostic regression corpus quality gates continue to pass;
* remediation safety behavior remains unchanged.

## Complete beginner example

This example demonstrates the complete contribution flow using synthetic data.

Suppose you want to diagnose this failure:

```text
java.net.UnknownHostException: synthetic-api.internal
```

Log Doctor already contains `UnknownHostIncident` and `UnknownHostRule`, so this example demonstrates how a contributor can understand and test an existing deterministic diagnosis.

### Step 1: Identify the diagnostic signal

The deterministic signal is:

```text
UnknownHostException
```

A matching log might be:

```text
ERROR request failed
java.net.UnknownHostException: synthetic-api.internal
    at com.example.client.ApiClient.call(ApiClient.java:42)
```

A near miss might be:

```text
DEBUG network retry scheduled after normal backoff
```

The near miss must remain unmatched. A rule should not classify normal network activity as an `UnknownHostException`.

### Step 2: Find the incident

The incident is located in:

```text
src/main/java/io/github/mathias82/logdoctor/incidents/UnknownHostIncident.java
```

The incident describes the diagnosis returned to the user.

### Step 3: Find or implement the rule

The matching rule is located in:

```text
src/main/java/io/github/mathias82/logdoctor/rules/UnknownHostRule.java
```

A deterministic rule follows this pattern:

```java
public class UnknownHostRule implements IncidentRule {

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        if (ctx.contextText().contains("UnknownHostException")) {
            UnknownHostIncident incident = new UnknownHostIncident();
            incident.setEvidence(ctx.contextText());
            return Optional.of(incident);
        }

        return Optional.empty();
    }
}
```

The rule returns an incident only when the expected deterministic signal is present.

### Step 4: Confirm rule registration

Open:

```text
src/main/java/io/github/mathias82/logdoctor/engine/IncidentDetector.java
```

Confirm that the specialized rule is included in `SPECIALIZED_RULES`:

```java
new UnknownHostRule(),
```

Built-in specialized rules are evaluated before `CommonFailureCatalogRule`.

### Step 5: Add a positive test

Create or update a focused rule test under:

```text
src/test/java/io/github/mathias82/logdoctor/rules/
```

A positive test should verify that the expected failure is detected:

```java
@Test
void detectsUnknownHostException() {
    Optional<Incident> incident = rule.match(
            new RuleContext(null, null,
                    "java.net.UnknownHostException: synthetic-api.internal"));

    assertThat(incident).isPresent();
    assertThat(incident.orElseThrow().type())
            .isEqualTo("UnknownHostException");
}
```

### Step 6: Add a negative or near-miss test

A near miss should verify that unrelated logs are not classified:

```java
@Test
void ignoresNormalNetworkRetry() {
    Optional<Incident> incident = rule.match(
            new RuleContext(null, null,
                    "DEBUG network retry scheduled after normal backoff"));

    assertThat(incident).isEmpty();
}
```

The positive test protects detection. The near-miss test protects precision.

### Step 7: Add regression corpus cases

Open:

```text
src/test/resources/diagnostic-benchmark/corpus.json
```

Add a positive case:

```json
{
  "name": "jvm-unknown-host-positive",
  "category": "JVM",
  "positive": true,
  "expectedRule": "UnknownHostRule",
  "log": "java.net.UnknownHostException: synthetic-api.internal"
}
```

Add a realistic negative or near-miss case:

```json
{
  "name": "jvm-unknown-host-near-miss",
  "category": "JVM",
  "positive": false,
  "expectedRule": null,
  "log": "DEBUG network retry scheduled after normal backoff"
}
```

Choose the appropriate existing corpus category: `JVM`, `SPRING`, `KAFKA`, or `DB`.

Keep the category balance and minimum corpus requirements intact.

### What this example demonstrates

```text
synthetic log pattern
        ↓
deterministic evidence
        ↓
Incident
        ↓
IncidentRule
        ↓
IncidentDetector registration
        ↓
positive test
        +
near-miss test
        ↓
diagnostic regression corpus
        ↓
local validation
```

Use the same process for a new deterministic diagnosis. The exact matching evidence may be more specific when an exception name or message is shared by multiple frameworks or subsystems.

## Adding a case to the diagnostic regression corpus

The labelled diagnostic regression corpus is:

```text
src/test/resources/diagnostic-benchmark/corpus.json
```

`DiagnosticBenchmarkTest` runs every corpus entry through `IncidentDetector`.

For a positive case:

* `positive` must be `true`;
* `expectedRule` should be the expected deterministic rule name;
* the log should contain synthetic or sanitized evidence.

Example:

```json
{
  "name": "jvm-example-positive",
  "category": "JVM",
  "positive": true,
  "expectedRule": "ExampleFailureRule",
  "log": "synthetic example failure"
}
```

For a negative or near-miss case:

* `positive` must be `false`;
* `expectedRule` should be `null`;
* no deterministic rule should match the log.

Example:

```json
{
  "name": "jvm-example-near-miss",
  "category": "JVM",
  "positive": false,
  "expectedRule": null,
  "log": "synthetic log that resembles the failure but is not the failure"
}
```

The benchmark groups cases into these categories:

```text
JVM
SPRING
KAFKA
DB
```

Choose the category that best represents the diagnostic being added.

A positive example protects recall and expected rule selection. A negative or near-miss example protects precision and false-positive rate.

Do not remove an existing regression case simply because a rule currently fails it. Fix the rule or discuss the intended behavior first.

## Local validation

Run validation from the repository root.

### Run all tests and verification

```bash
mvn clean verify
```

This runs the project's test suite and validation checks, including `DiagnosticBenchmarkTest`.

### Run a focused rule test

While developing a rule, run its test directly:

```bash
mvn -Dtest=UnknownHostRuleTest test
```

Replace `UnknownHostRuleTest` with the test class you are working on.

### Run the diagnostic benchmark

```bash
mvn -Dtest=DiagnosticBenchmarkTest test
```

The benchmark writes machine-readable metrics to:

```text
target/diagnostic-benchmark.json
```

Check that the benchmark passes its quality gates for:

* precision;
* recall;
* false-positive rate;
* exact rule accuracy;
* per-category metrics.

### Recommended validation sequence

Before opening a pull request, run:

```text
1. Focused rule test
2. Diagnostic benchmark
3. Full Maven verification
```

For example:

```bash
mvn -Dtest=UnknownHostRuleTest test
mvn -Dtest=DiagnosticBenchmarkTest test
mvn clean verify
```

If the full build fails because of an existing unrelated test failure, do not silently change unrelated production behavior to make the contribution pass. Record the failure and discuss it with the maintainers.

## Before opening a pull request

Check the following:

* [ ] The diagnostic signal is specific and deterministic.
* [ ] All examples use synthetic or sanitized data.
* [ ] The incident is reused when an existing diagnosis already applies.
* [ ] The rule returns an incident only when sufficient evidence is present.
* [ ] A positive test verifies the expected diagnosis.
* [ ] A negative or near-miss test protects against false positives.
* [ ] Rule precedence has been considered where another rule could also match.
* [ ] The regression corpus was updated where appropriate.
* [ ] The diagnostic benchmark passes.
* [ ] Local validation commands were run.
* [ ] No unrelated API or matching architecture changes were introduced.
* [ ] Automatic remediation remains disabled.
* [ ] `automaticExecutionAllowed=false` remains unchanged.

A good deterministic rule should improve diagnosis without reducing precision for unrelated logs.
