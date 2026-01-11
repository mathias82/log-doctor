# ✅ Supported Errors & Failure Categories

This document lists the production error types that **Log Doctor** can detect and diagnose.

Each supported error has:
- A deterministic detection rule
- A defined category
- A constrained set of allowed fixes

---

## 🗄️ DATABASE

### Hibernate LazyInitializationException
- Accessing lazy associations outside a transaction
- Detected via stack trace analysis

**Allowed Fixes**
- `@Transactional` on service method
- Repository query with `JOIN FETCH`
- DTO projection at repository level

---

### OptimisticLockingFailureException
- Concurrent updates to the same entity
- Version mismatch

**Automatic Fix**
- ❌ Not allowed  
- Requires human investigation

---

## ⚙️ CONFIGURATION

### NoSuchBeanDefinitionException
- Missing Spring bean
- Profile mismatch
- Conditional bean not loaded

**Allowed Fixes**
- Spring Java configuration
- Profile alignment
- Conditional annotations

---

### Spring Profile Mismatch
- Bean exists but not active due to profile

**Allowed Fixes**
- `spring.profiles.active` correction
- Align `@Profile` annotations

---

## 🔄 DESERIALIZATION

### Jackson MismatchedInputException
- Invalid JSON structure
- Type mismatch during deserialization

**Allowed Fixes**
- DTO correction
- Jackson annotations
- ObjectMapper configuration

---

## 🧵 THREADING / CONCURRENCY

### StaleObjectStateException
- Entity updated or deleted in another transaction

**Automatic Fix**
- ❌ Not allowed  
- Unsafe to auto-correct

---

## 📡 INFRASTRUCTURE

### Kafka Deserialization Errors
- Invalid message schema
- Payload incompatibility

**Allowed Fixes**
- Kafka CLI actions
- Consumer configuration
- ❌ Application code changes usually forbidden

---

## 🚫 Explicitly Unsupported (By Design)

The following are **intentionally not auto-fixed**:

- Data corruption
- Network partitions
- Split-brain scenarios
- Infrastructure outages
- Race conditions across services

Log Doctor will **refuse** to generate fixes for these cases.

---

## 🧠 Why This Matters

Most tools:
- Always suggest a fix
- Never refuse
- Hide uncertainty

Log Doctor:
- Knows when to act
- Knows when to stop
- Protects production systems

Safety is a feature.
