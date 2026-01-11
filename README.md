# 🩺 Log Doctor  
### Deterministic + LLM-powered Production Log Diagnosis for JVM / Spring / Kafka

Log Doctor is a **production-grade log analysis tool** that detects failures in JVM-based systems (Spring Boot, Hibernate, Kafka) and provides **precise root-cause analysis and safe fixes**.

It combines:

- **Deterministic rule-based detection** (HIGH confidence incidents)
- **Local LLM reasoning via Ollama** (for unknown or ambiguous logs)

Designed for **real production systems**, not toy examples.

---

## 🚀 Why Log Doctor?

Modern JVM applications produce massive logs, but:

- Stacktraces are noisy  
- Root causes are buried  
- Most tools only *display* logs — they don’t *understand* them  

**Log Doctor answers two critical questions:**

1. **Where exactly is the error?** (component + layer)  
2. **What is the safest possible fix?** (only if one exists)

No generic advice.  
No theory.  
No hallucinated fixes.

---

## ✨ Key Features

### ✅ Deterministic Incident Detection

Known production failures are detected with **HIGH confidence**, without LLM guessing:

- Hibernate `LazyInitializationException`
- Spring `NoSuchBeanDefinitionException`
- JSON / Jackson deserialization errors
- Kafka infrastructure failures
- Configuration & Spring profile mismatches

Each incident has:

- Category
- Severity
- Confidence
- Allowed fix types (policy-driven)

---

### 🧠 LLM-Assisted Diagnosis (Local, Safe)

For unknown failures, Log Doctor uses a **local LLM via Ollama** to:

- Identify the deepest application-level root cause
- Decide whether a **safe automatic fix exists**
- Refuse to propose fixes when **human investigation is required**

⚠️ No cloud APIs  
⚠️ No data leakage  
⚠️ No hallucinated infrastructure fixes  

---

## 🔐 Fix Safety by Design

Every fix is constrained by **FixPolicy**.

| Category | Allowed Fixes |
|--------|---------------|
| DATABASE | JAVA_CODE |
| CONFIGURATION | SPRING_CONFIG |
| DESERIALIZATION | JAVA_CODE / SPRING_CONFIG |
| INFRASTRUCTURE | KAFKA_CLI / NO_AUTOMATIC_FIX |
| THREADING | JAVA_CODE / NO_AUTOMATIC_FIX |

The LLM **cannot violate these rules**.

---

## 🧩 Architecture Overview

┌──────────────┐
│   Raw Logs   │
└──────┬───────┘
↓
┌────────────────────┐
│   LogParser        │
└──────┬─────────────┘
↓
┌────────────────────┐
│ FailureLocator     │  ← deepest root cause + blame location
└──────┬─────────────┘
↓
┌────────────────────┐
│ IncidentDetector   │  ← deterministic rules
└──────┬─────────────┘
↓
┌───────────────┬─────────────────────┐
│ Known Incident│ Unknown Failure     │
│ (HIGH CONF)   │                     │
│               │                     │
│ LLM Prompt    │ LLM Prompt          │
│ (constrained) │ (safe reasoning)    │
└───────────────┴─────────────────────┘


---

## 🧠 Failure Detection Strategy

### Root Cause Selection
- Prefer **deepest `Caused by`**
- Prefer **application-level exceptions**
- Avoid framework noise (`org.springframework`, `org.hibernate` internals)

### Blame Location
- First meaningful `com.*` stack frame
- Prefer `service` over `domain`
- Fallback to last meaningful application log

---

## 🧪 Example: Hibernate LazyInitializationException

### Input Log
```log
Caused by: org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role:
com.mycompany.myservice.domain.User.orders, could not initialize proxy - no Session
    at com.mycompany.myservice.service.UserService.toDto(UserService.java:74)```
---

### Output log

```WHERE:
UserService.toDto(UserService.java:74) – service – lazy association accessed outside transaction

FIX_TYPE: JAVA_CODE

FIX:
@Transactional(readOnly = true)
public UserDto getUser(Long id) {
    User user = userRepository.findByIdWithOrders(id);
    return UserDto.from(user);
}``` 

---
