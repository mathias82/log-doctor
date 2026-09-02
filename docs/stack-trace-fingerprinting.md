# Stack-trace-aware incident grouping

Log Doctor groups repeated failure blocks before optional Ollama enrichment. Grouping uses the normalized diagnosis fingerprint plus a stable stack-trace signature when a stack trace is present.

## Signature

The stack signature uses:

- the deepest visible `Exception`, `Error`, or `Throwable` type
- up to the first three frames belonging to that deepest visible cause
- class, method and source identity
- no source line numbers
- support for module-qualified JVM frames such as `java.base/java.lang.Thread.run(...)`
- stable handling of `Native Method` and `Unknown Source`
- suppressed failures do not replace the selected cause

For example, these frames produce the same stack signature even when a new build changes line numbers:

```text
at com.acme.OrderService.load(OrderService.java:41)
at com.acme.OrderController.get(OrderController.java:18)
```

```text
at com.acme.OrderService.load(OrderService.java:97)
at com.acme.OrderController.get(OrderController.java:33)
```

For nested failures, frames are associated with the deepest visible cause instead of combining the deepest exception type with frames from the outer wrapper. This makes the grouping identity internally consistent.

A failure from a different call path, such as `PaymentService.charge`, receives a different fingerprint even when the deterministic diagnosis type and root-cause text are identical.

## Why

Root-cause normalization already removes volatile IDs, numbers and UUIDs. That is useful for deduplication, but two independent code paths can still produce the same diagnosis and normalized root-cause text. Adding a stable call-path signal prevents those failures from being collapsed into one incident group.

## Safety and fallback

If no recognized exception/error/throwable signal is available, grouping falls back to the previous diagnosis fingerprint. Stack signatures are deterministic and local; they do not add an LLM call or send any additional data outside the existing analysis path.
