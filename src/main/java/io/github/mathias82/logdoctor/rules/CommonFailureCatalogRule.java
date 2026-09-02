package io.github.mathias82.logdoctor.rules;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Broad deterministic coverage for common Java/JVM, Spring, Hibernate/JPA,
 * JDBC/Hikari, Kafka and Schema Registry failures.
 *
 * <p>Specialized rules remain ahead of this catalog in {@code IncidentDetector},
 * so existing high-fidelity diagnoses keep precedence. This rule is the
 * deterministic catch layer before an unknown incident reaches the LLM fallback.</p>
 */
public final class CommonFailureCatalogRule implements IncidentRule {

    private static final List<Spec> SPECS = List.of(
            // JVM / Java / concurrency / networking
            spec("StackOverflowError", IncidentCategory.MEMORY, Severity.CRITICAL, "JVM", "Thread stack exhausted, usually because of deep or unbounded recursion.", "Inspect recursive call paths and termination conditions; only tune -Xss after fixing recursion.", "StackOverflowError"),
            spec("NoClassDefFoundError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "A runtime class is missing or failed static initialization.", "Align runtime dependencies and inspect the original class-initialization cause.", "NoClassDefFoundError"),
            spec("ClassNotFoundException", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "A requested class is absent from the runtime classpath.", "Add or correct the runtime dependency and verify packaging/module configuration.", "ClassNotFoundException"),
            spec("NoSuchFieldError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "Runtime bytecode references a field absent from the loaded class.", "Resolve binary-incompatible dependency versions using the dependency tree.", "NoSuchFieldError"),
            spec("AbstractMethodError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "A runtime implementation does not provide the API method expected by callers.", "Align API and implementation artifact versions.", "AbstractMethodError"),
            spec("IncompatibleClassChangeError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "Loaded classes are binary incompatible.", "Remove conflicting versions and converge related artifacts.", "IncompatibleClassChangeError"),
            spec("UnsupportedClassVersionError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "Bytecode targets a newer Java release than the running JVM.", "Run on a compatible JDK or compile to the intended release target.", "UnsupportedClassVersionError"),
            spec("VerifyError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "The JVM rejected invalid or incompatible bytecode.", "Inspect instrumentation, shading and bytecode/runtime version compatibility.", "VerifyError"),
            spec("LinkageError", IncidentCategory.CONFIGURATION, Severity.HIGH, "JVM", "Class linking failed because runtime classes are inconsistent.", "Inspect duplicate classes, classloaders and dependency convergence.", "LinkageError"),
            spec("ExceptionInInitializerError", IncidentCategory.TECHNICAL, Severity.HIGH, "JVM", "Static initialization threw an exception.", "Inspect the nested cause and reduce side effects in static initialization.", "ExceptionInInitializerError"),
            spec("ArithmeticException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "Java", "An arithmetic operation failed, commonly division by zero.", "Validate operands and guard exceptional arithmetic cases.", "ArithmeticException"),
            spec("NumberFormatException", IncidentCategory.DESERIALIZATION, Severity.MEDIUM, "Java", "Text does not match the expected numeric format.", "Validate input and handle parsing failures explicitly.", "NumberFormatException"),
            spec("DateTimeParseException", IncidentCategory.DESERIALIZATION, Severity.MEDIUM, "Java Time", "Date/time input does not match the configured formatter.", "Align formatter, locale and timezone assumptions with the producer format.", "DateTimeParseException"),
            spec("PatternSyntaxException", IncidentCategory.CONFIGURATION, Severity.MEDIUM, "Java Regex", "A configured regular expression has invalid syntax.", "Correct and validate the expression before use.", "PatternSyntaxException"),
            spec("FileNotFoundException", IncidentCategory.INFRASTRUCTURE, Severity.MEDIUM, "Filesystem", "A required file cannot be opened.", "Verify path, working directory, mounts and permissions.", "FileNotFoundException"),
            spec("NoSuchFileException", IncidentCategory.INFRASTRUCTURE, Severity.MEDIUM, "Filesystem", "A required filesystem path does not exist.", "Verify deployment mounts and path configuration.", "NoSuchFileException"),
            spec("NioAccessDeniedException", IncidentCategory.SECURITY, Severity.HIGH, "Filesystem", "The process is denied access to a filesystem resource.", "Review ownership, permissions, runtime user and mount settings.", "java.nio.file.AccessDeniedException"),
            spec("SSLHandshakeException", IncidentCategory.SECURITY, Severity.HIGH, "TLS", "TLS handshake failed.", "Inspect certificate chain, hostname/SANs, truststore, protocol/cipher compatibility and system time.", "SSLHandshakeException"),
            spec("SSLPeerUnverifiedException", IncidentCategory.SECURITY, Severity.HIGH, "TLS", "TLS peer identity could not be verified.", "Fix hostname/certificate identity alignment and trust configuration.", "SSLPeerUnverifiedException"),
            spec("CertificateExpiredException", IncidentCategory.SECURITY, Severity.CRITICAL, "TLS", "A certificate is outside its validity period.", "Rotate the certificate and verify automated renewal/expiry alerting.", "CertificateExpiredException"),
            spec("SocketTimeoutException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Network", "A network read/connect operation exceeded its timeout.", "Inspect downstream latency/connectivity before tuning timeouts or retries.", "SocketTimeoutException"),
            spec("ConnectionRefused", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Network", "A remote endpoint refused the connection.", "Verify service health, host, port, routing and firewall rules.", "Connection refused"),
            spec("SocketException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Network", "A socket-level network operation failed.", "Inspect peer health, proxies/load balancers and connection lifecycle.", "SocketException"),
            spec("RejectedExecutionException", IncidentCategory.THREADING, Severity.HIGH, "Concurrency", "An executor rejected submitted work.", "Inspect executor shutdown state, queue saturation, pool sizing and backpressure.", "RejectedExecutionException"),
            spec("CompletionException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "CompletableFuture", "An asynchronous computation wrapped an underlying exception.", "Inspect and preserve the nested cause.", "CompletionException"),
            spec("ExecutionException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "Concurrency", "An executor task failed with a nested exception.", "Inspect and preserve the nested cause.", "ExecutionException"),
            spec("ConcurrentTimeoutException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Concurrency", "An asynchronous operation exceeded its timeout.", "Identify the slow dependency or task and tune timeout/backpressure together.", "java.util.concurrent.TimeoutException"),
            spec("CancellationException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "Concurrency", "An asynchronous task was cancelled.", "Trace cancellation propagation and separate expected cancellation from failures.", "CancellationException"),
            spec("ClosedChannelException", IncidentCategory.INFRASTRUCTURE, Severity.MEDIUM, "NIO", "An operation targeted a closed channel.", "Inspect resource lifecycle and concurrent close/use paths.", "ClosedChannelException"),
            spec("BufferOverflowException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "NIO", "A buffer write exceeded remaining capacity.", "Correct buffer sizing and position/limit handling.", "BufferOverflowException"),
            spec("BufferUnderflowException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "NIO", "A buffer read expected more data than remains.", "Validate framing and remaining bytes before reads.", "BufferUnderflowException"),

            // Spring / Spring Boot / Spring Data
            spec("BeanCreationException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Spring", "Spring failed while creating a bean.", "Inspect the named bean and deepest nested constructor/factory/init cause.", "BeanCreationException"),
            spec("UnsatisfiedDependencyException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Spring", "Spring could not satisfy a bean dependency.", "Inspect constructor parameters, qualifiers, component scanning and nested causes.", "UnsatisfiedDependencyException"),
            spec("NoUniqueBeanDefinitionException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Spring", "Multiple beans match a single injection point.", "Use @Qualifier/@Primary or narrow the injected type.", "NoUniqueBeanDefinitionException"),
            spec("BeanDefinitionOverrideException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Spring", "Two configurations register a conflicting bean definition.", "Rename or remove the duplicate bean definition.", "BeanDefinitionOverrideException"),
            spec("ConfigurationPropertiesBindException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Spring Boot", "Configuration properties could not be bound.", "Check property names/types, active profiles and environment overrides.", "ConfigurationPropertiesBindException"),
            spec("MethodArgumentTypeMismatchException", IncidentCategory.DESERIALIZATION, Severity.MEDIUM, "Spring MVC", "A request parameter/path value cannot be converted to the controller type.", "Validate client input and expose a clear 4xx response.", "MethodArgumentTypeMismatchException"),
            spec("MissingServletRequestParameterException", IncidentCategory.DESERIALIZATION, Severity.LOW, "Spring MVC", "A required request parameter is missing.", "Fix the client request or make the parameter optional with validation.", "MissingServletRequestParameterException"),
            spec("HttpRequestMethodNotSupportedException", IncidentCategory.TECHNICAL, Severity.LOW, "Spring MVC", "The endpoint does not support the requested HTTP method.", "Use the mapped method or update endpoint mappings.", "HttpRequestMethodNotSupportedException"),
            spec("HttpMediaTypeNotSupportedException", IncidentCategory.DESERIALIZATION, Severity.MEDIUM, "Spring MVC", "The request content type is unsupported.", "Use a supported Content-Type or configure the required message converter.", "HttpMediaTypeNotSupportedException"),
            spec("HttpMediaTypeNotAcceptableException", IncidentCategory.TECHNICAL, Severity.MEDIUM, "Spring MVC", "No available representation satisfies the request Accept header.", "Align Accept headers and response media types/converters.", "HttpMediaTypeNotAcceptableException"),
            spec("WebClientResponseException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Spring WebClient", "A downstream HTTP service returned an error response.", "Inspect status/body and downstream health; retry only safe transient failures.", "WebClientResponseException"),
            spec("WebClientRequestException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Spring WebClient", "A downstream HTTP call failed before receiving a response.", "Inspect the nested DNS/connect/TLS/timeout cause.", "WebClientRequestException"),
            spec("DataAccessResourceFailureException", IncidentCategory.DATABASE, Severity.HIGH, "Spring Data", "A database or persistence resource is unavailable.", "Check database health, network, pool state and credentials.", "DataAccessResourceFailureException"),
            spec("CannotAcquireLockException", IncidentCategory.DATABASE, Severity.HIGH, "Spring Data", "A database lock could not be acquired.", "Inspect lock contention, transaction length/order and retry policy.", "CannotAcquireLockException"),
            spec("DeadlockLoserDataAccessException", IncidentCategory.DATABASE, Severity.HIGH, "Spring Data", "A transaction lost a database deadlock.", "Shorten transactions, enforce lock ordering and retry idempotent work only.", "DeadlockLoserDataAccessException"),
            spec("SpringQueryTimeoutException", IncidentCategory.DATABASE, Severity.HIGH, "Spring Data", "A database query exceeded its configured timeout.", "Inspect execution plan, indexes, blockers and timeout settings.", "org.springframework.dao.QueryTimeoutException"),
            spec("OptimisticLockingFailureException", IncidentCategory.DATABASE, Severity.MEDIUM, "Spring Data", "An optimistic-lock update lost a concurrent modification race.", "Reload and retry only when business semantics permit.", "OptimisticLockingFailureException"),
            spec("TransactionSystemException", IncidentCategory.DATABASE, Severity.HIGH, "Spring Transaction", "Transaction commit/rollback infrastructure failed.", "Inspect the deepest database or transaction-manager cause.", "TransactionSystemException"),
            spec("UnexpectedRollbackException", IncidentCategory.DATABASE, Severity.HIGH, "Spring Transaction", "A transaction was marked rollback-only before commit.", "Find the original exception and review transaction propagation boundaries.", "UnexpectedRollbackException"),

            // JPA / Hibernate / JDBC / Hikari
            spec("EntityNotFoundException", IncidentCategory.DATABASE, Severity.MEDIUM, "JPA/Hibernate", "A referenced entity row could not be found.", "Validate referential integrity and entity lookup assumptions.", "EntityNotFoundException"),
            spec("OptimisticLockException", IncidentCategory.DATABASE, Severity.MEDIUM, "JPA/Hibernate", "Another transaction changed the entity version first.", "Reload state and retry only when business semantics permit.", "OptimisticLockException"),
            spec("PessimisticLockException", IncidentCategory.DATABASE, Severity.HIGH, "JPA/Hibernate", "A pessimistic lock could not be acquired.", "Reduce lock scope/duration and inspect competing transactions.", "PessimisticLockException"),
            spec("JpaLockTimeoutException", IncidentCategory.DATABASE, Severity.HIGH, "JPA/Hibernate", "A persistence lock waited longer than allowed.", "Inspect blockers and reduce transaction/lock duration.", "jakarta.persistence.LockTimeoutException"),
            spec("NonUniqueResultException", IncidentCategory.DATABASE, Severity.MEDIUM, "JPA/Hibernate", "A query expected one row but returned multiple rows.", "Fix query/data uniqueness assumptions or use a multi-result API.", "NonUniqueResultException"),
            spec("PropertyValueException", IncidentCategory.DATABASE, Severity.HIGH, "Hibernate", "Hibernate found a null or invalid required property value.", "Populate required fields and persist associations in the correct order.", "PropertyValueException"),
            spec("TransientPropertyValueException", IncidentCategory.DATABASE, Severity.HIGH, "Hibernate", "An entity references an unsaved transient entity.", "Persist the referenced entity or configure cascade semantics correctly.", "TransientPropertyValueException"),
            spec("StaleObjectStateException", IncidentCategory.DATABASE, Severity.MEDIUM, "Hibernate", "Hibernate detected a stale entity version.", "Reload state and handle optimistic concurrency explicitly.", "StaleObjectStateException"),
            spec("StaleStateException", IncidentCategory.DATABASE, Severity.MEDIUM, "Hibernate", "Hibernate affected an unexpected number of rows.", "Inspect concurrent updates/deletes and entity session state.", "StaleStateException"),
            spec("SQLGrammarException", IncidentCategory.DATABASE, Severity.HIGH, "Hibernate/JDBC", "Generated or native SQL is invalid for the target database.", "Inspect SQL, schema state, object names and configured dialect.", "SQLGrammarException"),
            spec("JDBCConnectionException", IncidentCategory.DATABASE, Severity.CRITICAL, "Hibernate/JDBC", "Hibernate could not obtain or keep a JDBC connection.", "Check database availability, network, pool health and credentials.", "JDBCConnectionException"),
            spec("GenericJDBCException", IncidentCategory.DATABASE, Severity.HIGH, "Hibernate/JDBC", "Hibernate received an unclassified JDBC failure.", "Inspect SQLState/vendor code and the nested SQLException.", "GenericJDBCException"),
            spec("HibernateMappingException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Hibernate", "Hibernate mapping metadata is invalid.", "Inspect entity mappings, custom types and startup metadata errors.", "org.hibernate.MappingException"),
            spec("HibernateAnnotationException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Hibernate", "Entity annotations are inconsistent or invalid.", "Fix the named entity/property relationship mapping.", "org.hibernate.AnnotationException"),
            spec("MultipleBagFetchException", IncidentCategory.DATABASE, Severity.HIGH, "Hibernate", "A query attempts to join-fetch multiple bag/List associations.", "Split fetches or use Set/batch/subselect fetching where appropriate.", "MultipleBagFetchException"),
            spec("SqlIntegrityConstraintViolation", IncidentCategory.DATABASE, Severity.HIGH, "JDBC", "A database integrity constraint rejected a write.", "Fix data validation/order and inspect unique, FK, check and not-null constraints.", "SQLIntegrityConstraintViolationException"),
            spec("SqlSyntaxError", IncidentCategory.DATABASE, Severity.HIGH, "JDBC", "The database rejected SQL syntax or referenced objects.", "Verify SQL, schema migrations, quoting and dialect.", "SQLSyntaxErrorException"),
            spec("SqlTransientConnection", IncidentCategory.DATABASE, Severity.HIGH, "JDBC", "A transient database connection failure occurred.", "Check pool saturation and DB health; retry only with bounded backoff.", "SQLTransientConnectionException"),
            spec("SqlNonTransientConnection", IncidentCategory.DATABASE, Severity.CRITICAL, "JDBC", "A non-transient database connection failure occurred.", "Check URL, credentials, TLS, database state and driver compatibility.", "SQLNonTransientConnectionException"),
            spec("SqlRecoverableException", IncidentCategory.DATABASE, Severity.HIGH, "JDBC", "The JDBC operation may recover after reconnecting.", "Discard the broken connection and inspect DB/network stability.", "SQLRecoverableException"),
            spec("HikariPoolInitializationException", IncidentCategory.DATABASE, Severity.CRITICAL, "HikariCP", "HikariCP could not initialize the pool.", "Verify JDBC URL, driver, credentials, TLS and database availability.", "PoolInitializationException"),
            spec("HikariConnectionLeak", IncidentCategory.DATABASE, Severity.HIGH, "HikariCP", "HikariCP detected a connection held longer than the leak threshold.", "Close JDBC resources reliably and inspect long-running transactions.", "Apparent connection leak detected"),
            spec("SQLException", IncidentCategory.DATABASE, Severity.HIGH, "JDBC", "The JDBC driver reported an SQL operation failure.", "Inspect SQLState/vendor code, SQL text, schema and database logs.", "java.sql.SQLException"),

            // Kafka / Schema Registry
            spec("KafkaSerializationException", IncidentCategory.DESERIALIZATION, Severity.HIGH, "Kafka", "Kafka serialization or deserialization failed.", "Verify serializer configuration, payload type and schema compatibility.", "org.apache.kafka.common.errors.SerializationException"),
            spec("RecordDeserializationException", IncidentCategory.DESERIALIZATION, Severity.HIGH, "Kafka Consumer", "Kafka could not deserialize a consumed record.", "Inspect topic/partition/offset and fix deserializer/schema; use a recovery strategy for poison records.", "RecordDeserializationException"),
            spec("CommitFailedException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Consumer", "Offset commit failed after the consumer lost valid group membership.", "Reduce poll-thread processing time, tune max.poll.interval.ms or offload work safely.", "CommitFailedException"),
            spec("RebalanceInProgressException", IncidentCategory.INFRASTRUCTURE, Severity.MEDIUM, "Kafka Consumer", "The operation collided with an active consumer-group rebalance.", "Stabilize group membership and handle rebalance callbacks correctly.", "RebalanceInProgressException"),
            spec("WakeupException", IncidentCategory.TECHNICAL, Severity.LOW, "Kafka Consumer", "Consumer polling was interrupted via wakeup.", "Treat as expected during controlled shutdown; otherwise trace the wakeup caller.", "WakeupException"),
            spec("OffsetOutOfRangeException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Consumer", "Requested offsets are no longer available.", "Choose an explicit offset recovery policy and verify retention requirements.", "OffsetOutOfRangeException"),
            spec("NoOffsetForPartitionException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Kafka Consumer", "No committed offset exists and no usable reset policy is configured.", "Configure the intended auto.offset.reset behavior or initialize offsets explicitly.", "NoOffsetForPartitionException"),
            spec("GroupAuthorizationException", IncidentCategory.SECURITY, Severity.HIGH, "Kafka", "The principal is not authorized for the consumer group.", "Grant the minimum required group ACLs and verify principal mapping.", "GroupAuthorizationException"),
            spec("TopicAuthorizationException", IncidentCategory.SECURITY, Severity.HIGH, "Kafka", "The principal is not authorized for a topic operation.", "Grant the minimum required topic ACLs and verify the authenticated principal.", "TopicAuthorizationException"),
            spec("ClusterAuthorizationException", IncidentCategory.SECURITY, Severity.HIGH, "Kafka", "The principal lacks required cluster-level authorization.", "Grant only the required cluster ACL and verify principal mapping.", "ClusterAuthorizationException"),
            spec("SaslAuthenticationException", IncidentCategory.SECURITY, Severity.CRITICAL, "Kafka", "Kafka SASL authentication failed.", "Verify credentials/token, SASL mechanism, JAAS/client settings and broker listener configuration.", "SaslAuthenticationException"),
            spec("SslAuthenticationException", IncidentCategory.SECURITY, Severity.CRITICAL, "Kafka", "Kafka TLS authentication failed.", "Inspect keystore/truststore, certificates, hostname verification and broker listener config.", "SslAuthenticationException"),
            spec("KafkaAuthenticationException", IncidentCategory.SECURITY, Severity.CRITICAL, "Kafka", "Kafka client authentication failed.", "Verify credentials, security protocol, SASL/TLS settings and broker configuration.", "org.apache.kafka.common.errors.AuthenticationException"),
            spec("UnknownTopicOrPartitionException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka", "The requested topic or partition is unavailable.", "Verify topic existence/partition count and broker metadata health.", "UnknownTopicOrPartitionException"),
            spec("NotLeaderOrFollowerException", IncidentCategory.INFRASTRUCTURE, Severity.MEDIUM, "Kafka", "The contacted broker is no longer the leader/follower for the partition.", "Allow metadata refresh/retry and inspect broker/controller stability if persistent.", "NotLeaderOrFollowerException"),
            spec("LeaderNotAvailableException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka", "No partition leader is currently available.", "Inspect broker/controller health, ISR and replication state.", "LeaderNotAvailableException"),
            spec("NotEnoughReplicasException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Producer", "Kafka cannot satisfy the configured in-sync replica requirement.", "Restore broker/replica health before weakening durability settings.", "NotEnoughReplicasException"),
            spec("NotEnoughReplicasAfterAppendException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Producer", "A write was appended but insufficient replicas acknowledged it.", "Inspect ISR and broker health before retrying.", "NotEnoughReplicasAfterAppendException"),
            spec("RecordTooLargeException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Kafka Producer", "A record exceeds configured Kafka size limits.", "Reduce payload size or deliberately align producer, broker and topic size limits.", "RecordTooLargeException"),
            spec("MessageSizeTooLargeException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Kafka", "Kafka rejected an oversized message.", "Reduce payload size or align message-size settings across the full path.", "MessageSizeTooLargeException"),
            spec("InvalidTopicException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Kafka", "The requested topic name/configuration is invalid.", "Correct the topic name or configuration.", "InvalidTopicException"),
            spec("ProducerFencedException", IncidentCategory.INFRASTRUCTURE, Severity.CRITICAL, "Kafka Transactions", "A transactional producer was fenced by a newer producer epoch.", "Ensure transactional.id uniqueness per active producer and correct lifecycle management.", "ProducerFencedException"),
            spec("InvalidProducerEpochException", IncidentCategory.INFRASTRUCTURE, Severity.CRITICAL, "Kafka Transactions", "The producer epoch is stale.", "Recreate the producer and inspect duplicate transactional.id usage.", "InvalidProducerEpochException"),
            spec("OutOfOrderSequenceException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Producer", "Kafka detected an invalid idempotent producer sequence.", "Recreate the producer and inspect broker/session disruptions.", "OutOfOrderSequenceException"),
            spec("TransactionAbortedException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Transactions", "A Kafka transaction was aborted.", "Inspect the first transactional error, abort safely and retry only when appropriate.", "TransactionAbortedException"),
            spec("InvalidTxnStateException", IncidentCategory.TECHNICAL, Severity.HIGH, "Kafka Transactions", "A transactional API was called in an invalid lifecycle state.", "Correct begin/commit/abort/sendOffsets sequencing.", "InvalidTxnStateException"),
            spec("FencedInstanceIdException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka Consumer", "A static group member was fenced by another instance.", "Assign a unique group.instance.id to each active consumer instance.", "FencedInstanceIdException"),
            spec("CoordinatorNotAvailableException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka", "The group or transaction coordinator is unavailable.", "Retry with backoff and inspect broker/controller health if persistent.", "CoordinatorNotAvailableException"),
            spec("KafkaDisconnectException", IncidentCategory.INFRASTRUCTURE, Severity.MEDIUM, "Kafka", "The Kafka client connection was disconnected.", "Inspect broker availability, load balancers, TLS and network stability.", "org.apache.kafka.common.errors.DisconnectException"),
            spec("KafkaTimeoutException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Kafka", "A Kafka operation exceeded its timeout.", "Inspect broker/network/coordinator latency before tuning timeouts.", "org.apache.kafka.common.errors.TimeoutException"),
            spec("CorruptRecordException", IncidentCategory.DESERIALIZATION, Severity.HIGH, "Kafka", "Kafka detected corrupt record data.", "Inspect producer/broker/storage integrity and isolate the affected topic/partition/offset.", "CorruptRecordException"),
            spec("KafkaUnsupportedVersionException", IncidentCategory.CONFIGURATION, Severity.HIGH, "Kafka", "A Kafka request requires a protocol feature unsupported by client or broker.", "Align Kafka client/broker versions and feature settings.", "org.apache.kafka.common.errors.UnsupportedVersionException"),
            spec("SchemaRegistryUnauthorized", IncidentCategory.SECURITY, Severity.HIGH, "Schema Registry", "Schema Registry authentication or authorization failed.", "Verify API credentials/RBAC/ACLs and registry endpoint configuration.", "Unauthorized; error code:", "HTTP 401", "HTTP 403"),
            spec("SchemaRegistrySubjectNotFound", IncidentCategory.CONFIGURATION, Severity.HIGH, "Schema Registry", "A requested schema subject does not exist.", "Verify subject naming strategy, environment and registration lifecycle.", "Subject not found"),
            spec("SchemaRegistrySchemaNotFound", IncidentCategory.DESERIALIZATION, Severity.HIGH, "Schema Registry", "A referenced schema id/version is unavailable.", "Verify the configured registry/environment and schema lifecycle.", "Schema not found"),
            spec("SchemaRegistryRestClientException", IncidentCategory.INFRASTRUCTURE, Severity.HIGH, "Schema Registry", "Schema Registry returned a REST client error.", "Inspect HTTP status/error code, compatibility policy, auth and registry health.", "io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException")
    );

    @Override
    public Optional<Incident> match(RuleContext ctx) {
        String log = ctx.contextText();
        if (log == null || log.isBlank()) {
            return Optional.empty();
        }

        String normalized = log.toLowerCase(Locale.ROOT);
        for (Spec spec : SPECS) {
            Optional<String> marker = spec.markers().stream()
                    .filter(m -> normalized.contains(m.toLowerCase(Locale.ROOT)))
                    .findFirst();
            if (marker.isEmpty()) {
                continue;
            }

            CatalogIncident incident = new CatalogIncident(
                    spec.type(),
                    spec.category(),
                    spec.severity(),
                    Confidence.HIGH,
                    spec.component(),
                    spec.type() + " detected in the failure context.",
                    spec.rootCause(),
                    spec.recommendation()
            );
            incident.setEvidence(extractEvidence(log, marker.get()));
            return Optional.of(incident);
        }

        return Optional.empty();
    }

    static int catalogSize() {
        return SPECS.size();
    }

    private static String extractEvidence(String log, String marker) {
        String needle = marker.toLowerCase(Locale.ROOT);
        return log.lines()
                .filter(line -> line.toLowerCase(Locale.ROOT).contains(needle))
                .findFirst()
                .map(String::trim)
                .orElse(marker + " detected");
    }

    private static Spec spec(
            String type,
            IncidentCategory category,
            Severity severity,
            String component,
            String rootCause,
            String recommendation,
            String... markers
    ) {
        return new Spec(type, category, severity, component, rootCause, recommendation, Arrays.asList(markers));
    }

    private record Spec(
            String type,
            IncidentCategory category,
            Severity severity,
            String component,
            String rootCause,
            String recommendation,
            List<String> markers
    ) {}
}
