package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.rules.*;

import java.util.List;
import java.util.Optional;

public class IncidentDetector {

    private final List<IncidentRule> rules = List.of(
            new HibernateLazyInitRule(),
            new KafkaSchemaIncompatibleRule(),
            new HikariTimeoutRule(),
            new SpringConfigBindRule(),
            new SpringProfileMismatchRule(),
            new KafkaRebalanceLoopRule(),
            new CircularDependencyRule(),
            new OutOfMemoryRule(),
            new ThreadStarvationRule(),
            new GcThrashingRule(),
            new DeadlockRule(),
            new KafkaTopicNotFoundRule(),
            new IllegalStateBusinessRule(),
            new NullInputRule(),
            new JavaTimeSerializationRule(),
            new MissingSpringBeanRule(),
            new JacksonLocalDateTimeRule(),
            new IndexOutOfBoundsRule(),
            new NullPointerExceptionRule(),
            new IllegalArgumentExceptionRule(),
            new NoSuchElementExceptionRule(),
            new ClassCastExceptionRule(),
            new MethodArgumentNotValidRule(),
            new HttpMessageNotReadableRule(),
            new ConstraintViolationRule(),
            new DataIntegrityViolationRule(),
            new TransactionRequiredRule(),
            new ConnectTimeoutRule(),
            new UnknownHostRule(),
            new UnsupportedOperationRule(),
            new ConcurrentModificationRule(),
            new NoSuchMethodErrorRule(),
            new BeanCurrentlyInCreationRule(),
            new AccessDeniedExceptionRule(),
            new KafkaJsonDeserializationRule(),
            new PersistenceConcurrencyRule(),
            new SpringBootStartupFailureRule(),
            new CommonFailureCatalogRule()
    );

    public Optional<Incident> detect(RuleContext context) {
        return detectDetailed(context).map(Detection::incident);
    }

    public Optional<Detection> detectDetailed(RuleContext context) {
        for (IncidentRule rule : rules) {
            Optional<Incident> incident = rule.match(context);
            if (incident.isPresent()) {
                Incident matched = incident.get();
                String evidence = matched.evidence();
                List<String> reasons = evidence == null || evidence.isBlank()
                        ? List.of("Matched deterministic rule " + rule.getClass().getSimpleName())
                        : List.of(
                                "Matched deterministic rule " + rule.getClass().getSimpleName(),
                                "Matching evidence: " + firstLine(evidence)
                        );
                return Optional.of(new Detection(matched, rule.getClass().getSimpleName(), reasons));
            }
        }
        return Optional.empty();
    }

    private static String firstLine(String value) {
        return value.lines().findFirst().orElse(value).trim();
    }

    public record Detection(
            Incident incident,
            String rule,
            List<String> reasons
    ) {}
}
