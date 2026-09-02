# Stack-trace-aware incident grouping

Log Doctor groups repeated failure blocks before optional Ollama enrichment. Grouping now uses the existing normalized diagnosis fingerprint plus a stable stack-trace signature when a stack trace is present.

## Signature

The stack signature uses:

- the deepest visible exception/error type
- up to the first three stack frames
- class, method and source file name
- no source line numbers

For example, these frames produce the same stack signature even when a new build changes line numbers:

```text
at com.acme.OrderService.load(OrderService.java:41)
at com.acme.OrderController.get(OrderController.java:18)
```

```text
at com.acme.OrderService.load(OrderService.java:97)
at com.acme.OrderController.get(OrderController.java:33)
```

A failure from a different call path, such as `PaymentService.charge`, receives a different fingerprint even when the deterministic diagnosis type and root-cause text are identical.

## Why

Root-cause normalization already removes volatile IDs, numbers and UUIDs. That is useful for deduplication, but two independent code paths can still produce the same diagnosis and normalized root-cause text. Adding a stable call-path signal prevents those failures from being collapsed into one incident group.

## Safety and fallback

If no exception/error or stack frame is available, grouping falls back to the previous diagnosis fingerprint. Stack signatures are deterministic and local; they do not add an LLM call or send any additional data outside the existing analysis path.
