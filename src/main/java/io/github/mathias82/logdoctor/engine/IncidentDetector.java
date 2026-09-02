package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.rules.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public class IncidentDetector {

    private static final List<IncidentRule> SPECIALIZED_RULES = List.of(
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
            new KafkaOperationalFailureRule()
    );

    private final List<IncidentRule> rules;

    /**
     * Builds the default detector and loads additional deterministic rules from
     * {@link IncidentRuleProvider} implementations visible through {@link ServiceLoader}.
     * Extension rules run after Log Doctor's specialized rules and before the broad
     * common-failure catalog, preserving built-in precedence while allowing custom
     * organization/domain diagnostics to beat the generic catch layer.
     */
    public IncidentDetector() {
        this(loadExtensionRules());
    }

    /**
     * Builds a detector with explicit extension rules. This is useful for embedded
     * integrations and tests that do not want classpath-wide ServiceLoader discovery.
     */
    public IncidentDetector(List<IncidentRule> extensionRules) {
        List<IncidentRule> ordered = new ArrayList<>(SPECIALIZED_RULES);
        if (extensionRules != null) {
            extensionRules.stream().filter(rule -> rule != null).forEach(ordered::add);
        }
        ordered.add(new CommonFailureCatalogRule());
        this.rules = List.copyOf(ordered);
    }

    private static List<IncidentRule> loadExtensionRules() {
        List<IncidentRule> loaded = new ArrayList<>();
        ServiceLoader.load(IncidentRuleProvider.class).forEach(provider -> {
            List<IncidentRule> provided = provider.rules();
            if (provided != null) {
                provided.stream().filter(rule -> rule != null).forEach(loaded::add);
            }
        });
        return List.copyOf(loaded);
    }

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
