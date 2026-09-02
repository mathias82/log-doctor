package io.github.mathias82.logdoctor.engine;

import java.util.List;

/**
 * Service-provider interface for adding deterministic incident rules without
 * modifying Log Doctor core.
 *
 * <p>Providers can be registered with the standard Java ServiceLoader mechanism
 * using {@code META-INF/services/io.github.mathias82.logdoctor.engine.IncidentRuleProvider}.
 * Returned rules run after Log Doctor's specialized built-in rules and before the
 * broad common-failure catalog.</p>
 */
public interface IncidentRuleProvider {

    List<IncidentRule> rules();
}
