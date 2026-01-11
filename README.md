# 🩺 Log Doctor — Deterministic + LLM-Powered Log Diagnosis for JVM / Spring / Kafka

**Advanced Production Log Analysis for JVM-based Systems (Spring Boot, Hibernate, Kafka)**  
Built for real-world production environments. Privacy-first, deterministic, and safe.

[📚 View Detailed Docs →](https://github.com/mathias82/log-doctor/tree/main/docs)

---

## 🔍 Overview

**Log Doctor** detects and diagnoses production incidents with **high confidence** using two combined strategies:

- ⚙️ **Deterministic rule-based detection** — for known, production-grade failures.  
- 🧠 **Local LLM reasoning (via Ollama)** — for unknown or ambiguous logs.  

No cloud APIs. No hallucinations. No unsafe fixes.

---

## 🚀 Why Log Doctor?

Modern JVM systems produce enormous, noisy logs.  
Stacktraces hide real issues. Most tools **show logs** — but don’t **understand** them.

**Log Doctor** answers two critical questions:

1. **Where exactly is the failure?** (component + layer)  
2. **Can a safe, deterministic fix be proposed?**

> ✅ No generic advice  
> ✅ No theory dumps  
> ✅ No fake AI confidence  

---

## ✨ Key Features

- **Deterministic incident detection** — Hibernate, Kafka, Spring, JSON, Threading, etc.  
- **Local LLM diagnosis** — privacy-preserving, no external API calls.  
- **FixPolicy safety system** — defines what can be auto-fixed.  
- **Refusal mechanism** — knows when *not* to act.  

---

## 🧱 Incident & Diagnosis Model

An **Incident** represents a reproducible production failure with structured metadata.

### 📄 Structure

| Field | Description |
|------|-------------|
| **Type** | Human-readable name of the incident |
| **Category** | DATABASE / CONFIGURATION / DESERIALIZATION / THREADING / INFRASTRUCTURE |
| **Severity** | Impact level (LOW → CRITICAL) |
| **Confidence** | Detection certainty (LOW / MEDIUM / HIGH) |
| **Summary** | One-line human summary |
| **Root Cause** | Specific technical explanation |
| **Recommendation** | Fix strategy or refusal reason |
| **Evidence** | Log excerpt proving the incident |

### 🧠 Detection Strategy

**Two-tier detection pipeline:**

1️⃣ **Deterministic Detection**  
- Rule-based pattern recognition  
- Stack trace depth and package filtering  
- Noise suppression for framework internals  

2️⃣ **LLM-Assisted Diagnosis**  
- Local reasoning via Ollama  
- Constrained models (Llama3, Mistral, etc.)  
- Safety-aware and privacy-preserving  

### 🎯 Root Cause Selection Rules

- Prefer **deepest `Caused by`** exception  
- Focus on **application-level** errors  
- Deprioritize `org.springframework.*`, `org.hibernate.internal.*`, proxies

### 📍 Blame Location Resolution

- First meaningful `com.*` stack frame  
- Prefer service over domain layers  
- Fallback: last meaningful log message  

---

## ✅ Supported Errors & Failure Categories

This section summarizes all the deterministic rules and supported incident types.  
📘 *See also:* [Supported Errors Documentation →](https://github.com/mathias82/log-doctor/tree/main/docs)

### 🗄️ DATABASE

#### Hibernate LazyInitializationException
- Accessing lazy associations outside of a transaction.  
- Detected via stack trace & root-cause parsing.

**Allowed Fixes**
- `@Transactional` on service method  
- Repository query with `JOIN FETCH`  
- DTO projection at repository level  

#### OptimisticLockingFailureException
- Concurrent entity updates, version mismatch.  
**Automatic Fix:** ❌ *Not allowed (human review required)*

---

### ⚙️ CONFIGURATION

#### NoSuchBeanDefinitionException
- Missing or misconfigured Spring bean.  
- Profile mismatch or conditional bean not loaded.

**Allowed Fixes**
- Java/Spring configuration update  
- Profile alignment  
- Adjust `@Conditional` annotations  

#### Spring Profile Mismatch
- Bean exists but inactive due to `@Profile` settings.  
**Allowed Fixes**
- Correct `spring.profiles.active`  
- Align annotation values  

---

### 🔄 DESERIALIZATION

#### Jackson MismatchedInputException
- JSON structure mismatch with DTO schema.

**Allowed Fixes**
- DTO correction  
- Jackson annotation adjustment  
- ObjectMapper tuning  

---

### 🧵 THREADING / CONCURRENCY

#### DeadlockRule / ThreadStarvationRule
- Thread pool exhaustion or circular lock.

**Fix Policy**
- ❌ Auto-fix prohibited (requires manual intervention)

---

### 📡 INFRASTRUCTURE

#### KafkaTopicNotFoundRule / KafkaSchemaIncompatibleRule / KafkaRebalanceLoopRule
- Invalid topic or schema configuration.  
- Message schema mismatch or rebalance loop detected.

**Allowed Fixes**
- CLI actions via Kafka utilities  
- Configuration alignment  
- ❌ No code-level fix

---

### 💾 MEMORY & GC

#### GcThrashingRule / OutOfMemoryRule
- Excessive GC cycles or heap exhaustion.  

**Fix Policy**
- ❌ No auto-fix  
- Suggest JVM memory tuning or code-level refactor

---

### 🧩 Other Deterministic Rules

| Rule | Description |
|------|-------------|
| **CircularDependencyRule** | Spring circular bean reference |
| **HikariTimeoutRule** | Database pool connection timeout |
| **SpringConfigBindRule** | Configuration binding failure |
| **SpringProfileMismatchRule** | Inactive or misaligned profile |
| **ThreadStarvationRule** | Thread pool deadlock condition |

---

## 🧪 Example: Hibernate LazyInitializationException

### Input Log
```java
Caused by: org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role:
com.mycompany.myservice.domain.User.orders, could not initialize proxy - no Session
at com.mycompany.myservice.service.UserService.toDto(UserService.java:74)
```

### Output Diagnosis
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

✅ *Detected deterministically — no LLM reasoning needed.*

---

## 🦙 Running Log Doctor with Ollama (Local LLM)

### Installation

```bash
# 1️⃣ Install Ollama
brew install ollama   # macOS
# or visit: https://ollama.com

# 2️⃣ Pull a local model
ollama pull llama3

# 3️⃣ Start the Ollama service
ollama serve

# 4️⃣ Run Log Doctor
java -jar log-doctor-0.1.0.jar --file examples/app.log
```

> Connects automatically to `http://localhost:11434`

---

## ⚙️ Configuration

| Setting | Description |
|----------|--------------|
| `model` | Choose Ollama model (llama3, mistral, codellama) |
| `contextRadius` | Adjust log context window |
| `enableLlmFallback` | Enable/disable LLM reasoning |
| `fixPolicyMode` | Enforce or relax fix constraints |

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
├── rules/
│   ├── CircularDependencyRule
│   ├── DeadlockRule
│   ├── GcThrashingRule
│   ├── HibernateLazyInitRule
│   ├── HikariTimeoutRule
│   ├── KafkaRebalanceLoopRule
│   ├── KafkaSchemaIncompatibleRule
│   ├── KafkaTopicNotFoundRule
│   ├── OutOfMemoryRule
│   ├── SpringConfigBindRule
│   ├── SpringProfileMismatchRule
│   ├── ThreadStarvationRule
│   └── LogDoctorApplication
└── docs/
    ├── [incidents.md](https://github.com/mathias82/log-doctor/blob/main/docs/incidents.md)
    └── [supported-errors.md](https://github.com/mathias82/log-doctor/blob/main/docs/supported-errors.md)
```

---

## 🧭 Philosophy

- **Determinism before AI**  
- **Safety before automation**  
- **Local-first, privacy-first**  
- **Production realism over demos**

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
