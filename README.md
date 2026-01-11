# 🩺 Log Doctor
**Deterministic + LLM-powered Production Log Diagnosis for JVM / Spring / Kafka**

> A deterministic Java log analyzer for Spring Boot, Hibernate and Kafka,
> powered by local LLMs (Ollama) for safe production diagnostics.

---

## 🧭 Overview
**Log Doctor** is a **production-grade log analysis tool** that detects failures in JVM-based systems  
(**Spring Boot**, **Hibernate**, **Kafka**) and provides **precise root-cause analysis** and **safe fixes**.

It combines:

- ⚙️ **Deterministic rule-based detection** (HIGH confidence incidents)
- 🧠 **Local LLM reasoning via Ollama** (for unknown or ambiguous logs)

> 🧩 Designed for real production systems, not toy examples.

---

## 🚀 Why Log Doctor?

Modern JVM applications produce **massive logs**, but:

- Stacktraces are noisy
- Root causes are buried
- Most tools only display logs — they don’t *understand* them

**Log Doctor answers two critical questions:**

1. 🔍 Where exactly is the error? (*component + layer*)
2. 🧯 What is the safest possible fix? (*only if one exists*)

❌ No generic advice  
❌ No theory  
❌ No hallucinated fixes  

---

## ✨ Key Features

### ✅ Deterministic Incident Detection

Known production failures are detected with **HIGH confidence**, without LLM guessing:

- Hibernate LazyInitializationException
- Spring NoSuchBeanDefinitionException
- Spring profile mismatches
- Jackson / JSON deserialization failures
- Kafka topic not found
- Kafka schema incompatibility
- HikariCP timeouts
- Deadlocks & thread starvation
- OutOfMemoryError
- GC thrashing

📄 Full list:
👉 [docs/supported-errors.md](https://github.com/mathias82/log-doctor/blob/main/docs/supported-errors.md)

Each incident includes:

- **Category**
- **Severity**
- **Confidence**
- **Allowed Fix Types (policy-driven)**

Each supported error is implemented as an independent deterministic rule
under the `rules/` package and can be enabled, disabled or extended without
affecting the rest of the system.

---

### 🧠 LLM-Assisted Diagnosis (Local, Safe)

For unknown failures, Log Doctor uses a **local LLM via Ollama** to:

- Identify the *deepest application-level root cause*
- Decide whether a *safe automatic fix* exists
- Refuse to propose fixes when human investigation is required

⚠️ No cloud APIs  
⚠️ No data leakage  
⚠️ No hallucinated infra fixes  

---

## 🔐 Fix Safety by Design

Every fix is constrained by a **FixPolicy**:

| Category | Allowed Fixes |
|--------|---------------|
| DATABASE | JAVA_CODE |
| CONFIGURATION | SPRING_CONFIG |
| DESERIALIZATION | JAVA_CODE / SPRING_CONFIG |
| INFRASTRUCTURE | KAFKA_CLI / NO_AUTOMATIC_FIX |
| THREADING | JAVA_CODE / NO_AUTOMATIC_FIX |

> The LLM **cannot violate** these rules.

---

## 🧩 Architecture Overview

```
Raw Logs
   ↓
LogParser
   ↓
FailureLocator   (root cause + blame location)
   ↓
IncidentDetector (deterministic rules)
   ↓
┌───────────────┬─────────────────────┐
│ Known Incident│ Unknown Failure     │
│ (HIGH CONF)   │                     │
│ LLM Prompt    │ LLM Prompt          │
│ (constrained) │ (safe reasoning)    │
└───────────────┴─────────────────────┘
```

---

## 🧠 How Rules Are Applied
- Logs are parsed line by line
- The deepest application-level failure is selected
- Deterministic rules are evaluated first
- LLM is used **only** if no rule matches

---

## ▶️ End-to-End Demo Flow
Raw log → Root cause → Blame location → Fix (or refusal)

### 🧪 Example: Hibernate `LazyInitializationException`

**Input Log**
```
2026-05-10 10:14:33.412 ERROR [http-nio-8080-exec-4] o.s.web.servlet.DispatcherServlet :
Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception

org.springframework.web.util.NestedServletException: Request processing failed
    at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
    at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:898)

Caused by: org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role:
com.mycompany.myservice.domain.User.orders, could not initialize proxy - no Session
    at org.hibernate.collection.internal.AbstractPersistentCollection.throwLazyInitializationException(AbstractPersistentCollection.java:614)
    at org.hibernate.collection.internal.AbstractPersistentCollection.withTemporarySessionIfNeeded(AbstractPersistentCollection.java:218)
    at org.hibernate.collection.internal.AbstractPersistentCollection.initialize(AbstractPersistentCollection.java:591)
    at org.hibernate.collection.internal.AbstractPersistentCollection.read(AbstractPersistentCollection.java:149)
    at com.mycompany.myservice.service.UserService.toDto(UserService.java:74)
    at com.mycompany.myservice.api.UserController.getUser(UserController.java:52)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
```

**Output**
```
WHERE:
 - component: UserService
 - layer: SERVICE
 - method or line: UserService.toDto(UserService.java:74)

FIX_TYPE: JAVA_CODE

FIX:
@Transactional
public UserDto toDto(User user) {
    return new UserDto(user.getId(), user.getName(),
                      Optional.ofNullable(user.getOrders()).orElse(Collections.emptyList()));
}
```
<img width="1703" height="1090" alt="image" src="https://github.com/user-attachments/assets/8b0f5d3c-2519-4927-85e7-c26962370dfa" />

---

## ❌ When Log Doctor REFUSES to Fix

Some failures **must not be auto-fixed**, e.g.:

- Optimistic locking conflicts
- Concurrent updates
- Cross-transaction data consistency issues

```
No safe automatic fix – human investigation required.
```

This is a **feature**, not a limitation.

---

## 🦙 Running Log Doctor with Ollama (Local LLM)

### 1️⃣ Install Ollama
👉 https://ollama.com

### 2️⃣ Pull a Model
```bash
ollama pull llama3
```

Other supported models:
- mistral
- codellama
- llama3:instruct

### 3️⃣ Start Ollama
```bash
ollama serve
```

Ollama runs on:
```
http://localhost:11434
```

### 4️⃣ Run Log Doctor
```bash
java -jar log-doctor-0.1.0.jar --file examples/app.log
```

---

## 📦 Project Structure
```
log-doctor/
├── core/
├── engine/
├── llm/
├── rules/
└── examples/
```

---

## 🎯 Who Is This For?
- Backend engineers (Java / Spring)
- Platform & DevOps engineers
- Kafka & microservices teams
- Anyone debugging production logs at 3AM

---

## 🧭 Philosophy
- Determinism before AI
- Safety before automation
- Local-first, privacy-first
- Production realism over demos

---

## 📄 License

Apache 2.0 License — use it, extend it, improve it.

---

## 🌐 SEO Keywords

`log doctor`, `spring boot logs`, `java stacktrace analyzer`, `kafka deserialization error`,  
`hibernate lazy initialization`, `ollama local llm`, `deterministic log diagnosis`,  
`spring bean missing`, `java production debugging`, `gc thrashing analysis`

---

## ⭐ Final Note

If your tool:
- Always proposes a fix → ❌ *It’s lying*  
- Never refuses → ⚠️ *It’s dangerous*  
- Explains theory only → 💤 *It’s not production-ready*  

> 🩺 **Log Doctor does none of the above.**  
> [Visit Documentation →](https://github.com/mathias82/log-doctor/tree/main/docs)
