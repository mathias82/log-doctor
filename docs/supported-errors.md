# ✅ Supported Errors & Failure Categories

Log Doctor uses deterministic rules before optional LLM reasoning. Specialized rules handle high-fidelity cases first; the common failure catalog then recognizes a broad set of well-known Java/JVM, Spring, Hibernate/JPA, JDBC/Hikari, Kafka and Schema Registry failures before an incident is considered unknown.

Each deterministic result carries a type, category, severity, confidence, component, evidence, root-cause guidance and recommendation. Fix generation is still constrained by the existing safety policy; detection does **not** imply that Log Doctor will auto-fix the incident.

## Java / JVM / concurrency / networking

Representative coverage includes:

- `StackOverflowError`
- `NoClassDefFoundError`
- `ClassNotFoundException`
- `NoSuchFieldError`
- `AbstractMethodError`
- `IncompatibleClassChangeError`
- `UnsupportedClassVersionError`
- `VerifyError`
- `LinkageError`
- `ExceptionInInitializerError`
- `ArithmeticException`
- `NumberFormatException`
- `DateTimeParseException`
- `PatternSyntaxException`
- filesystem access/missing-file failures
- TLS handshake, peer-verification and certificate-expiry failures
- socket/connect/timeout failures
- executor rejection, cancellation and async wrapper failures
- NIO closed-channel and buffer under/overflow failures

Existing specialized rules continue to cover common Java failures such as null/index/class-cast/concurrent-modification cases, JVM OOM, GC thrashing, thread starvation and deadlocks.

## Spring / Spring Boot / Spring Data

Representative coverage includes:

- `BeanCreationException`
- `UnsatisfiedDependencyException`
- `NoUniqueBeanDefinitionException`
- `BeanDefinitionOverrideException`
- `ConfigurationPropertiesBindException`
- request argument/type/media/method failures
- `WebClientRequestException`
- `WebClientResponseException`
- `DataAccessResourceFailureException`
- `CannotAcquireLockException`
- `DeadlockLoserDataAccessException`
- query timeout and optimistic-lock failures
- `TransactionSystemException`
- `UnexpectedRollbackException`

Specialized rules retain precedence for existing Spring configuration/profile, validation, missing-bean and HTTP-message diagnoses.

## Hibernate / JPA / JDBC / HikariCP

Representative coverage includes:

- `EntityNotFoundException`
- `OptimisticLockException`
- `PessimisticLockException`
- JPA lock timeout
- `NonUniqueResultException`
- `PropertyValueException`
- `TransientPropertyValueException`
- `StaleObjectStateException`
- `StaleStateException`
- `SQLGrammarException`
- `JDBCConnectionException`
- `GenericJDBCException`
- Hibernate mapping/annotation failures
- `MultipleBagFetchException`
- JDBC integrity, syntax, transient/non-transient connection and recoverable failures
- Hikari pool initialization and connection-leak detection
- generic `SQLException` fallback

The existing specialized `LazyInitializationException`, transaction-required, data-integrity and Hikari timeout rules remain ahead of the broad catalog.

## Kafka / Schema Registry

Representative coverage includes:

- Kafka serialization and `RecordDeserializationException`
- `CommitFailedException`
- `RebalanceInProgressException`
- `WakeupException`
- `OffsetOutOfRangeException`
- `NoOffsetForPartitionException`
- topic/group/cluster authorization failures
- SASL, SSL and generic Kafka authentication failures
- `UnknownTopicOrPartitionException`
- `NotLeaderOrFollowerException`
- `LeaderNotAvailableException`
- insufficient-replica failures
- oversized-record/message failures
- `ProducerFencedException`
- `InvalidProducerEpochException`
- `OutOfOrderSequenceException`
- transaction-aborted/invalid-transaction-state failures
- `FencedInstanceIdException`
- coordinator unavailable, disconnect and Kafka timeout failures
- `CorruptRecordException`
- Kafka protocol version mismatch
- Schema Registry auth, missing-subject, missing-schema and REST client failures

Existing specialized Kafka topic, rebalance, JSON deserialization and schema-compatibility rules retain precedence.

## Matching and precedence

The broad catalog is intentionally registered **after** the specialized rules. That means an error already understood by a richer rule keeps the richer diagnosis. Catalog matching is case-insensitive and captures the matching log line as evidence.

Unknown or application-specific failures are still allowed to fall through to the optional local Ollama path. The catalog is not intended to pretend every possible exception is known; it reduces unnecessary LLM use for common production failures.

## Safety

Infrastructure outages, data corruption, race conditions, network partitions and similar operational failures may be detected, but detection does not make them safe to auto-correct. `NO_AUTOMATIC_FIX` and the existing category-based fix policy remain authoritative.

**Determinism before AI. Evidence before causation claims. Safety before automation.**
