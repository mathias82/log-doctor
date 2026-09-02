package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.rules.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class IncidentDetector {

    private static final Logger LOG = LoggerFactory.getLogger(IncidentDetector.class);

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
     *
     * <p>Extension failures are isolated from core diagnosis: provider loading and
     * extension rule runtime failures are logged and skipped so a broken third-party
     * rule cannot take down the analyzer.</p>
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
            extensionRules.stream()
                    .filter(rule -> rule != null)
                    .map(SafeExtensionRule::new)
                    .forEach(ordered::add);
        }
        ordered.add(new CommonFailureCatalogRule());
        this.rules = List.copyOf(ordered);
    }

    private static List<IncidentRule> loadExtensionRules() {
        List<IncidentRule> loaded = new ArrayList<>();
        try {
            ServiceLoader.load(IncidentRuleProvider.class).forEach(provider -> {
                try {
                    List<IncidentRule> provided = provider.rules();
                    if (provided != null) {
                        provided.stream().filter(rule -> rule != null).forEach(loaded::add);
                    }
                } catch (RuntimeException e) {
                    LOG.warn("Skipping IncidentRuleProvider {} because rules() failed: {}",
                            provider.getClass().getName(), e.toString());
                }
            });
        } catch (ServiceConfigurationError error) {
            LOG.warn("Stopping IncidentRuleProvider discovery after ServiceLoader failure: {}", error.toString());
        }
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
                        ? List.of("Matched deterministic rule " + ruleName(rule))
                        : List.of(
                                "Matched deterministic rule " + ruleName(rule),
                                "Matching evidence: " + firstLine(evidence)
                        );
                return Optional.of(new Detection(matched, ruleName(rule), reasons));
            }
        }
        return Optional.empty();
    }

    private static String ruleName(IncidentRule rule) {
        return rule instanceof SafeExtensionRule safe ? safe.delegateName() : rule.getClass().getSimpleName();
    }

    private static String firstLine(String value) {
        return value.lines().findFirst().orElse(value).trim();
    }

    private static final class SafeExtensionRule implements IncidentRule {
        private final IncidentRule delegate;

        private SafeExtensionRule(IncidentRule delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Incident> match(RuleContext context) {
            try {
                Optional<Incident> result = delegate.match(context);
                return result == null ? Optional.empty() : result;
            } catch (RuntimeException e) {
                LOG.warn("Skipping extension rule {} after match failure: {}", delegateName(), e.toString());
                return Optional.empty();
            }
        }

        private String delegateName() {
            String simpleName = delegate.getClass().getSimpleName();
            return simpleName.isBlank() ? delegate.getClass().getName() : simpleName;
        }
    }

    public record Detection(
            Incident incident,
            String rule,
            List<String> reasons
    ) {}
}
