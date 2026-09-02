# Custom deterministic rule providers

Log Doctor can load organization-specific deterministic rules without changing the core repository.

## Provider SPI

Implement `IncidentRuleProvider` and return one or more `IncidentRule` instances:

```java
package com.acme.logdoctor;

import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.IncidentRuleProvider;

import java.util.List;

public final class AcmeRuleProvider implements IncidentRuleProvider {
    @Override
    public List<IncidentRule> rules() {
        return List.of(new AcmePaymentFailureRule());
    }
}
```

Register the provider using the standard Java `ServiceLoader` descriptor:

```text
META-INF/services/io.github.mathias82.logdoctor.engine.IncidentRuleProvider
```

with the provider class name as its content:

```text
com.acme.logdoctor.AcmeRuleProvider
```

When `new IncidentDetector()` is created, Log Doctor discovers registered providers from the runtime classpath.

## Rule precedence

The order is deliberate:

1. specialized Log Doctor built-in rules
2. extension rules returned by `IncidentRuleProvider`
3. `CommonFailureCatalogRule`

That means custom rules can refine failures that would otherwise fall into the broad deterministic catalog, but cannot silently replace Log Doctor's higher-fidelity specialized diagnoses.

## Programmatic registration

Embedded applications that do not want classpath-wide discovery can pass rules explicitly:

```java
IncidentDetector detector = new IncidentDetector(List.of(new AcmePaymentFailureRule()));
```

The same precedence applies: explicit extension rules are inserted after specialized built-ins and before the common catalog.

## Safety

Custom rules participate only in deterministic incident detection. They do not gain automatic remediation authority. The existing `FixPolicy`, remediation safety metadata, `NO_AUTOMATIC_FIX`, and `automaticExecutionAllowed=false` contracts remain authoritative downstream.

Provider code is trusted application code loaded from the runtime classpath. Keep providers small, deterministic, side-effect free, and focused on matching/diagnosis rather than executing changes.
