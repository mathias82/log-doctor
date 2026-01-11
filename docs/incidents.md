# 🩺 Incidents & Diagnosis Model

This document describes how **Log Doctor** models, detects, and diagnoses production incidents
in JVM-based systems (Spring Boot, Hibernate, Kafka).

Log Doctor does **not** treat logs as plain text.
It transforms logs into **deterministic incidents** with strict safety rules.

---

## 🔍 What Is an Incident?

An **Incident** represents a production failure that:

- Has a clearly identifiable root cause
- Occurs at the application level (not noise)
- Can be classified by category, severity, and confidence
- May or may not have a **safe automatic fix**

Each incident answers two questions:

1. **WHERE** exactly is the error?
2. **Can a safe fix be proposed?**

---

## 🧱 Incident Structure

Every incident contains the following fields:

| Field | Description |
|------|-------------|
| Type | Human-readable incident name |
| Category | Failure category (DATABASE, CONFIGURATION, etc.) |
| Severity | Impact level (LOW → CRITICAL) |
| Confidence | Detection certainty (LOW / MEDIUM / HIGH) |
| Summary | One-line description |
| Root Cause | Precise technical cause |
| Recommendation | Fix strategy or refusal |
| Evidence | Relevant log excerpt |

---

## 🧠 Detection Strategy

Log Doctor uses **two complementary mechanisms**:

### 1️⃣ Deterministic Detection (HIGH Confidence)

Known failure patterns are detected using strict rules:

- Exception type
- Stack trace depth
- Application package matching
- Noise filtering (framework internals removed)

These incidents **do not rely on LLM reasoning**.

### 2️⃣ LLM-Assisted Diagnosis (Unknown Failures)

If no deterministic rule matches:

- Logs are analyzed using a **local LLM via Ollama**
- The model is **heavily constrained**
- Unsafe fixes are explicitly refused

---

## 🎯 Root Cause Selection Rules

To avoid false positives:

- Prefer the **deepest `Caused by`** exception
- Prefer **application-level exceptions**
- Deprioritize:
  - `org.springframework.*`
  - `org.hibernate.internal.*`
  - Proxy / wrapper exceptions

---

## 📍 Blame Location Resolution

Log Doctor identifies **where the bug manifests in your code**:

1. First meaningful `com.*` stack frame
2. Prefer `service` over `domain`
3. Fallback to last relevant application log line

---

## ❌ When Log Doctor Refuses to Fix

Some incidents must **not** be auto-fixed, including:

- Optimistic locking conflicts
- Concurrent update races
- Cross-transaction consistency errors
- Data corruption scenarios

In these cases, the output is **exactly**:

This is intentional and enforced by design.

---

## 🧭 Design Principles

- Determinism before AI
- Safety before automation
- Refusal is a valid outcome
- Production realism over demos

Log Doctor is built for **real systems**, not tutorials.

