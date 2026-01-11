# 🩺 Log Doctor  
**Deterministic + LLM-powered Production Log Diagnosis for JVM / Spring / Kafka**

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

> ❌ No generic advice  
> ❌ No theory  
> ❌ No hallucinated fixes  

---

## ✨ Key Features

### ✅ Deterministic Incident Detection

Known production failures are detected with **HIGH confidence**, without LLM guessing:

- Hibernate `LazyInitializationException`  
- Spring `NoSuchBeanDefinitionException`  
- JSON / Jackson deserialization errors  
- Kafka infrastructure failures  
- Configuration & profile mismatches  

Each incident includes:

- **Category**  
- **Severity**  
- **Confidence**  
- **Allowed Fix Types (policy-driven)**  

---

### 🧠 LLM-Assisted Diagnosis (Local, Safe)

For unknown failures, Log Doctor uses a **local LLM via Ollama** to:

- Identify the *deepest application-level root cause*  
- Decide whether a *safe automatic fix* exists  
- Refuse to propose fixes when human investigation is required  

> ⚠️ No cloud APIs  
> ⚠️ No data leakage  
> ⚠️ No hallucinated infra fixes  

---

## 🔐 Fix Safety by Design

Every fix is constrained by a **FixPolicy**:

| **Category**        | **Allowed Fixes**                |
|----------------------|----------------------------------|
| DATABASE             | JAVA_CODE                        |
| CONFIGURATION        | SPRING_CONFIG                    |
| DESERIALIZATION      | JAVA_CODE / SPRING_CONFIG         |
| INFRASTRUCTURE       | KAFKA_CLI / NO_AUTOMATIC_FIX      |
| THREADING            | JAVA_CODE / NO_AUTOMATIC_FIX      |

> The LLM **cannot violate** these rules.

---

## 🧩 Architecture Overview

```
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
```

---

## 🧠 Failure Detection Strategy

### Root Cause Selection
- Prefer **deepest `Caused by`**  
- Prefer **application-level exceptions**  
- Avoid framework noise (`org.springframework`, `org.hibernate` internals)

### Blame Location
- First meaningful `com.*` stack frame  
- Prefer **service** over **domain**  
- Fallback to last meaningful application log  

---

## 🧪 Example: Hibernate `LazyInitializationException`

### Input Log
```
Caused by: org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role:
com.mycompany.myservice.domain.User.orders, could not initialize proxy - no Session
at com.mycompany.myservice.service.UserService.toDto(UserService.java:74)
```

### Output
```
WHERE:
UserService.toDto(UserService.java:74) – service – lazy association accessed outside transaction

FIX_TYPE: JAVA_CODE

FIX:
@Transactional(readOnly = true)
public UserDto getUser(Long id) {
    User user = userRepository.findByIdWithOrders(id);
    return UserDto.from(user);
}
```

---

## ❌ When Log Doctor REFUSES to Fix

Some failures **must not be auto-fixed**, e.g.:

- Optimistic locking conflicts  
- Concurrent updates  
- Cross-transaction data consistency issues  

> 🧩 *No safe automatic fix – human investigation required.*

**This is a feature, not a limitation.**

---

## 🦙 Running Log Doctor with Ollama (Local LLM)

Log Doctor uses **Ollama** to run **LLMs locally**.

### 1️⃣ Install Ollama  
👉 [https://ollama.com](https://ollama.com)

Available for:
- macOS  
- Linux  
- Windows  

### 2️⃣ Pull a Model
Recommended (fast + accurate):
```bash
ollama pull llama3
```
Other supported models:
- `mistral`
- `codellama`
- `llama3:instruct`

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

> Log Doctor automatically connects to Ollama.

---

## ⚙️ Configuration

**Default:**  
No configuration required.

**Optional (future-ready):**
- Change model  
- Adjust context radius  
- Enable/disable LLM fallback  

---

## 📦 Project Structure

```
log-doctor/
├── core/
│   ├── Incident.java
│   ├── FixPolicy.java
│   └── enums/
├── engine/
│   ├── LogParser
│   ├── FailureLocator
│   ├── IncidentDetector
│   └── DiagnosisEngine
├── llm/
│   ├── LlmPrompts
│   ├── LlmClient
│   └── OllamaLlmClient
└── examples/
```

---

## 🎯 Who Is This For?

- 🧑‍💻 Backend engineers (Java / Spring)  
- ⚙️ Platform & DevOps engineers  
- ☕ Teams running Kafka & microservices  
- 🌙 Anyone debugging production logs at 3 AM  

---

## 🧭 Philosophy

- ⚙️ Determinism before AI  
- 🛡️ Safety before automation  
- 🔒 Local-first, privacy-first  
- 🚀 Production realism over demos  

---

## 📄 License

**MIT License** – use it, extend it, break it, improve it.

---

## ⭐ Final Note

If your tool:

- always proposes a fix → ❌ *it’s lying*  
- never refuses → ⚠️ *it’s dangerous*  
- explains theory → 💤 *it’s not production-ready*  

> 🩺 **Log Doctor does none of the above.**
