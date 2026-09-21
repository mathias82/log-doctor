package io.github.mathias82.logdoctor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.Severity;
import io.github.mathias82.logdoctor.engine.DiagnosisEngine;
import io.github.mathias82.logdoctor.engine.IncidentDetector;
import io.github.mathias82.logdoctor.engine.IncidentRule;
import io.github.mathias82.logdoctor.engine.RuleContext;
import io.github.mathias82.logdoctor.engine.RuleFailureListener;
import io.github.mathias82.logdoctor.incidents.CatalogIncident;
import io.github.mathias82.logdoctor.llm.LlmClient;
import io.github.mathias82.logdoctor.observability.RuntimeMetrics;
import io.github.mathias82.logdoctor.web.LogDoctorWebServer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PublicContractCompatibilityIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SNAPSHOT_RESOURCE = "/compatibility/public-contract-v1.json";
    private static final Path REPORT_PATH = Path.of("target", "public-contract-compatibility.json");

    @Test
    void protectedPublicContractsRemainBackwardCompatible() throws Exception {
        JsonNode snapshot = loadSnapshot();
        Map<String, CheckedContract> checks = new LinkedHashMap<>();
        checks.put("http-api", () -> checkHttpApi(snapshot.path("http")));
        checks.put("cli", () -> checkCli(snapshot.path("cli")));
        checks.put("github-action", () -> checkGitHubAction(snapshot.path("githubAction")));
        checks.put("sarif", () -> checkSarif(snapshot.path("sarif")));
        checks.put("remediation-safety", () -> checkRemediationSafety(snapshot.path("remediationSafety")));
        checks.put("prometheus-metrics", () -> checkPrometheusMetrics(snapshot.path("prometheusMetricNames")));
        checks.put("rule-provider-spi", () -> checkRuleProviderSpi(snapshot.path("spi")));
        checks.put("public-java-api", () -> checkTypeContracts(snapshot.path("javaApi").path("types")));

        ArrayNode surfaceResults = JSON.createArrayNode();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, CheckedContract> entry : checks.entrySet()) {
            ObjectNode result = surfaceResults.addObject();
            result.put("surface", entry.getKey());
            try {
                entry.getValue().check();
                result.put("status", "PASSED");
            } catch (Exception | AssertionError failure) {
                String detail = usefulMessage(failure);
                result.put("status", "FAILED");
                result.put("detail", detail);
                failures.add(entry.getKey() + ": " + detail);
            }
        }

        ObjectNode report = JSON.createObjectNode();
        report.put("schemaVersion", 1);
        report.put("contractSnapshot", SNAPSHOT_RESOURCE);
        report.put("contractLine", snapshot.path("contractLine").asText());
        report.put("overallStatus", failures.isEmpty() ? "PASSED" : "FAILED");
        report.set("surfaces", surfaceResults);
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");

        String failureSummary = String.join(System.lineSeparator() + "- ", failures);
        assertThat(failures)
                .withFailMessage("Public contract compatibility failures:%n- %s", failureSummary)
                .isEmpty();
    }

    private static void checkHttpApi(JsonNode contract) throws Exception {
        JsonNode version = contract.path("version");
        assertThat(LogDoctorWebServer.API_VERSION_HEADER)
                .as("HTTP API version header constant")
                .isEqualTo(version.path("header").asText());
        assertThat(LogDoctorWebServer.API_VERSION)
                .as("HTTP API version value constant")
                .isEqualTo(version.path("value").asText());
        String apiContractDocumentation = Files.readString(Path.of("docs", "api-contract.md"));
        assertThat(apiContractDocumentation)
                .as("documented HTTP API contract version")
                .contains("Current API contract version: `" + version.path("value").asText() + "`")
                .contains(version.path("header").asText() + ": " + version.path("value").asText());

        HttpServer server = LogDoctorWebServer.start(0);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> healthResponse = get(client, baseUrl + "/api/health");
            assertSuccessfulVersionedResponse(healthResponse, version, "GET /api/health");
            JsonNode health = JSON.readTree(healthResponse.body());
            requireFields(health, contract.path("health").path("requiredFields"), "HTTP health response");
            assertThat(health.path("status").asText()).isEqualTo("UP");
            assertThat(health.path("apiVersion").asText()).isEqualTo(version.path("value").asText());

            HttpResponse<String> analyzeResponse = postLog(
                    client,
                    baseUrl + "/api/analyze",
                    "org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]");
            assertSuccessfulVersionedResponse(analyzeResponse, version, "POST /api/analyze");
            JsonNode analyze = JSON.readTree(analyzeResponse.body());
            requireFields(analyze, contract.path("analyze").path("requiredFields"), "HTTP analyze response");
            requireFields(
                    analyze.path("remediation"),
                    loadSnapshot().path("remediationSafety").path("requiredFields"),
                    "HTTP analyze remediation");
            requireFields(
                    analyze.path("redactionReport"),
                    contract.path("redactionReportRequiredFields"),
                    "HTTP analyze redaction report");

            String batchLog = """
                    2026-09-01 14:32:17 ERROR request failed
                    org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [orders]
                    """;
            HttpResponse<String> batchResponse = postLog(client, baseUrl + "/api/analyze/batch", batchLog);
            assertSuccessfulVersionedResponse(batchResponse, version, "POST /api/analyze/batch");
            JsonNode batch = JSON.readTree(batchResponse.body());
            requireFields(batch, contract.path("batch").path("requiredFields"), "HTTP batch response");
            assertThat(batch.path("incidents").isEmpty())
                    .as("HTTP batch response must contain a representative incident for the contract probe")
                    .isFalse();
            JsonNode incident = batch.path("incidents").get(0);
            requireFields(
                    incident,
                    contract.path("batch").path("requiredIncidentFields"),
                    "HTTP batch incident");
            requireFields(
                    incident.path("grouping"),
                    contract.path("batch").path("requiredGroupingFields"),
                    "HTTP batch grouping metadata");
            requireFields(
                    incident.path("remediation"),
                    loadSnapshot().path("remediationSafety").path("requiredFields"),
                    "HTTP batch remediation");
            requireFields(
                    batch.path("redactionReport"),
                    contract.path("redactionReportRequiredFields"),
                    "HTTP batch redaction report");
        } finally {
            server.stop(0);
        }
    }

    private static void checkCli(JsonNode contract) {
        for (JsonNode format : contract.path("formats")) {
            String expected = format.asText();
            assertThat(AnalyzeCommand.resolveFormat(
                    new String[]{"--file", "app.log", "--format", expected}))
                    .as("CLI format %s", expected)
                    .isEqualTo(expected);
        }
        assertThat(AnalyzeCommand.resolveFormat(new String[]{"--file", "app.log"}))
                .as("default CLI format")
                .isEqualTo(contract.path("defaultFormat").asText());

        for (JsonNode failOn : contract.path("failOnValues")) {
            String expected = failOn.asText();
            assertThat(AnalyzeCommand.resolveFailOn(
                    new String[]{"--file", "app.log", "--fail-on", expected}))
                    .as("CLI --fail-on value %s", expected)
                    .isEqualTo(expected);
        }
        assertThat(AnalyzeCommand.resolveFailOn(new String[]{"--file", "app.log"}))
                .as("default CLI --fail-on value")
                .isEqualTo(contract.path("defaultFailOn").asText());

        assertThat(AnalyzeCommand.EXIT_OK).isEqualTo(contract.path("exitCodes").path("ok").asInt());
        assertThat(AnalyzeCommand.EXIT_POLICY_MATCHED)
                .isEqualTo(contract.path("exitCodes").path("policyMatched").asInt());
        assertThat(AnalyzeCommand.EXIT_USAGE_OR_ANALYSIS_ERROR)
                .isEqualTo(contract.path("exitCodes").path("usageOrAnalysisError").asInt());

        DiagnosisEngine engine = engine();
        for (JsonNode sample : contract.path("failOnMatrix")) {
            DiagnosisEngine.DiagnosisResult result = engine.analyzeStructured(sample.path("log").asText());
            if (sample.has("status")) {
                assertThat(result.status()).as("CLI policy sample %s status", sample.path("name").asText())
                        .isEqualTo(sample.path("status").asText());
            }
            if (sample.has("severity")) {
                assertThat(result.severity()).as("CLI policy sample %s severity", sample.path("name").asText())
                        .isEqualTo(sample.path("severity").asText());
            }
            Iterator<Map.Entry<String, JsonNode>> outcomes = sample.path("outcomes").fields();
            while (outcomes.hasNext()) {
                Map.Entry<String, JsonNode> outcome = outcomes.next();
                assertThat(AnalyzeCommand.shouldFail(result, outcome.getKey()))
                        .as("CLI --fail-on %s for %s", outcome.getKey(), sample.path("name").asText())
                        .isEqualTo(outcome.getValue().asBoolean());
            }
        }
    }

    private static void checkGitHubAction(JsonNode contract) throws Exception {
        Path actionPath = Path.of("action.yml");
        String actionText = Files.readString(actionPath);
        Map<String, Map<String, String>> inputs = parseYamlSection(actionText, "inputs");
        Map<String, Map<String, String>> outputs = parseYamlSection(actionText, "outputs");

        checkActionEntries(inputs, contract.path("inputs"), "input");
        checkActionEntries(outputs, contract.path("outputs"), "output");
        for (JsonNode fragment : contract.path("requiredRunFragments")) {
            assertThat(actionText).as("GitHub Action command wiring").contains(fragment.asText());
        }
    }

    private static void checkSarif(JsonNode contract) throws Exception {
        DiagnosisEngine engine = engine();
        for (JsonNode sample : contract.path("samples")) {
            DiagnosisEngine.DiagnosisResult result = engine.analyzeStructured(sample.path("log").asText());
            String severity = sample.path("severity").asText();
            assertThat(result.severity()).as("SARIF %s probe severity", severity).isEqualTo(severity);

            JsonNode sarif = JSON.readTree(CiOutputFormatter.sarif(result, Path.of("logs", "app.log")));
            assertThat(sarif.path("version").asText()).isEqualTo(contract.path("version").asText());
            JsonNode run = sarif.path("runs").get(0);
            JsonNode rule = run.path("tool").path("driver").path("rules").get(0);
            JsonNode finding = run.path("results").get(0);
            String expectedRuleId = sample.path("ruleId").asText();
            assertThat(rule.path("id").asText())
                    .as("SARIF rule id for %s", severity)
                    .startsWith(contract.path("ruleIdPrefix").asText())
                    .isEqualTo(expectedRuleId);
            assertThat(finding.path("ruleId").asText()).isEqualTo(expectedRuleId);
            assertThat(finding.path("level").asText())
                    .as("SARIF level for %s", severity)
                    .isEqualTo(sample.path("level").asText());
        }
    }

    private static void checkRemediationSafety(JsonNode contract) throws Exception {
        DiagnosisEngine engine = engine();
        List<String> safetyProbes = List.of(
                "java.lang.NullPointerException: order was null",
                "java.lang.OutOfMemoryError: Java heap space",
                contract.path("manualOnlySample").path("log").asText());

        for (String log : safetyProbes) {
            DiagnosisEngine.DiagnosisResult result = engine.analyzeStructured(log);
            JsonNode remediation = JSON.valueToTree(result.remediation());
            requireFields(remediation, contract.path("requiredFields"), "remediation metadata");
            requireFields(
                    remediation.path("playbook"),
                    contract.path("requiredPlaybookFields"),
                    "remediation playbook");
            assertThat(remediation.path("automaticExecutionAllowed").asBoolean())
                    .as("automatic remediation execution must stay disabled")
                    .isEqualTo(contract.path("automaticExecutionAllowed").asBoolean());
        }

        JsonNode manualContract = contract.path("manualOnlySample");
        DiagnosisEngine.DiagnosisResult manual = engine.analyzeStructured(manualContract.path("log").asText());
        assertThat(manual.fixType()).isEqualTo(manualContract.path("fixType").asText());
        assertThat(manual.humanReviewRequired())
                .isEqualTo(manualContract.path("humanReviewRequired").asBoolean());
        assertThat(manual.remediation().safety()).isEqualTo(manualContract.path("safety").asText());
        assertThat(manual.remediation().allowedActions())
                .contains(manualContract.path("allowedAction").asText());
        assertThat(manual.remediation().automaticExecutionAllowed()).isFalse();

        JsonNode agent = JSON.readTree(AgentOutputFormatter.agent(manual, Path.of("logs", "app.log")));
        assertThat(agent.path("safety").has("automaticExecutionAllowed")).isTrue();
        assertThat(agent.path("safety").path("automaticExecutionAllowed").asBoolean()).isFalse();
    }

    private static void checkPrometheusMetrics(JsonNode expectedNames) {
        RuntimeMetrics metrics = new RuntimeMetrics();
        metrics.record("DIAGNOSED", false, 25_000_000L, 1);
        metrics.recordError();
        metrics.recordRuleProviderFailure();

        Set<String> actualNames = metrics.prometheusText().lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.substring(0, line.indexOf(' ')))
                .map(name -> name.contains("{") ? name.substring(0, name.indexOf('{')) : name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> requiredNames = new LinkedHashSet<>();
        expectedNames.forEach(name -> requiredNames.add(name.asText()));

        assertThat(actualNames)
                .as("stable Prometheus metric names; additive names are allowed")
                .containsAll(requiredNames);
    }

    private static void checkRuleProviderSpi(JsonNode contract) throws Exception {
        checkTypeContracts(contract.path("interfaces"));

        for (JsonNode behavior : contract.path("behaviors")) {
            switch (behavior.asText()) {
                case "specialized-built-ins-before-extensions" -> specializedRulesKeepPrecedence();
                case "extensions-before-common-catalog" -> extensionsBeatCommonCatalog();
                case "first-extension-match-wins" -> firstExtensionMatchWins();
                case "extension-failures-are-isolated-and-reported" -> extensionFailuresAreIsolatedAndReported();
                default -> throw new AssertionError("No compatibility probe implements SPI behavior " + behavior.asText());
            }
        }
    }

    private static void specializedRulesKeepPrecedence() {
        IncidentRule extension = context -> Optional.of(customIncident("CUSTOM_NPE"));
        String actual = new IncidentDetector(List.of(extension))
                .detectDetailed(context("java.lang.NullPointerException: order was null"))
                .orElseThrow()
                .incident()
                .type();
        assertThat(actual).as("specialized built-in rule precedence").isEqualTo("NullPointerException");
    }

    private static void extensionsBeatCommonCatalog() {
        IncidentRule extension = context -> context.contextText().contains("ClassNotFoundException")
                ? Optional.of(customIncident("CUSTOM_CLASSLOAD"))
                : Optional.empty();
        String actual = new IncidentDetector(List.of(extension))
                .detectDetailed(context("java.lang.ClassNotFoundException: com.acme.LegacyAdapter"))
                .orElseThrow()
                .incident()
                .type();
        assertThat(actual).as("extension precedence over the common catalog").isEqualTo("CUSTOM_CLASSLOAD");
    }

    private static void firstExtensionMatchWins() {
        IncidentRule first = context -> Optional.of(customIncident("FIRST_EXTENSION"));
        IncidentRule second = context -> Optional.of(customIncident("SECOND_EXTENSION"));
        String actual = new IncidentDetector(List.of(first, second))
                .detectDetailed(context("custom deterministic failure"))
                .orElseThrow()
                .incident()
                .type();
        assertThat(actual).as("first matching extension rule").isEqualTo("FIRST_EXTENSION");
    }

    private static void extensionFailuresAreIsolatedAndReported() {
        int[] failures = {0};
        IncidentRule broken = context -> {
            throw new IllegalStateException("extension exploded");
        };
        IncidentRule recovery = context -> Optional.of(customIncident("RECOVERED_EXTENSION"));
        RuleFailureListener listener = () -> failures[0]++;

        String actual = new IncidentDetector(List.of(broken, recovery), listener)
                .detectDetailed(context("custom deterministic failure"))
                .orElseThrow()
                .incident()
                .type();
        assertThat(actual).as("rule evaluation after extension failure").isEqualTo("RECOVERED_EXTENSION");
        assertThat(failures[0]).as("extension failure notification count").isEqualTo(1);
    }

    private static void checkTypeContracts(JsonNode types) throws Exception {
        for (JsonNode expectedType : types) {
            String className = expectedType.path("name").asText();
            Class<?> type = Class.forName(className);
            assertThat(Modifier.isPublic(type.getModifiers())).as("public type %s", className).isTrue();

            for (JsonNode constructor : expectedType.path("constructors")) {
                Class<?>[] parameters = parameterTypes(constructor);
                assertThat(type.getConstructor(parameters))
                        .as("public constructor %s(%s)", className, parameterNames(parameters))
                        .isNotNull();
            }
            if (expectedType.has("canonicalConstructor")) {
                Class<?>[] parameters = parameterTypes(expectedType.path("canonicalConstructor"));
                assertThat(type.getConstructor(parameters))
                        .as("public canonical constructor %s(%s)", className, parameterNames(parameters))
                        .isNotNull();
            }

            for (JsonNode expectedMethod : expectedType.path("methods")) {
                String methodName = expectedMethod.path("name").asText();
                Class<?>[] parameters = parameterTypes(expectedMethod.path("parameters"));
                var method = type.getMethod(methodName, parameters);
                assertThat(method.getReturnType().getName())
                        .as("return type of %s#%s", className, methodName)
                        .isEqualTo(expectedMethod.path("returns").asText());
            }

            if (expectedType.has("recordComponents")) {
                assertThat(type.isRecord()).as("record type %s", className).isTrue();
                Map<String, String> actualComponents = Arrays.stream(type.getRecordComponents())
                        .collect(Collectors.toMap(
                                component -> component.getName(),
                                component -> component.getType().getName(),
                                (first, second) -> first,
                                LinkedHashMap::new));
                Iterator<Map.Entry<String, JsonNode>> expectedComponents = expectedType.path("recordComponents").fields();
                while (expectedComponents.hasNext()) {
                    Map.Entry<String, JsonNode> component = expectedComponents.next();
                    assertThat(actualComponents)
                            .as("record component %s#%s", className, component.getKey())
                            .containsEntry(component.getKey(), component.getValue().asText());
                }
            }
        }
    }

    private static void requireFields(JsonNode actual, JsonNode requiredFields, String label) {
        assertThat(actual.isObject()).as("%s must be a JSON object", label).isTrue();
        Iterator<Map.Entry<String, JsonNode>> fields = requiredFields.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            assertThat(actual.has(field.getKey()))
                    .as("%s field %s", label, field.getKey())
                    .isTrue();
            JsonNode value = actual.get(field.getKey());
            assertThat(matchesJsonType(value, field.getValue().asText()))
                    .as("%s field %s must remain %s but was %s",
                            label, field.getKey(), field.getValue().asText(), value.getNodeType())
                    .isTrue();
        }
    }

    private static boolean matchesJsonType(JsonNode value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> throw new IllegalArgumentException("Unsupported snapshot JSON type: " + expectedType);
        };
    }

    private static void assertSuccessfulVersionedResponse(
            HttpResponse<String> response,
            JsonNode version,
            String endpoint
    ) {
        assertThat(response.statusCode()).as("%s status", endpoint).isEqualTo(200);
        assertThat(response.headers().firstValue(version.path("header").asText()))
                .as("%s API version header", endpoint)
                .contains(version.path("value").asText());
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postLog(HttpClient client, String url, String log) throws Exception {
        String body = JSON.writeValueAsString(Map.of("log", log));
        return client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void checkActionEntries(
            Map<String, Map<String, String>> actual,
            JsonNode expected,
            String entryKind
    ) {
        Iterator<Map.Entry<String, JsonNode>> entries = expected.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            assertThat(actual).as("GitHub Action %s %s", entryKind, entry.getKey()).containsKey(entry.getKey());
            Iterator<Map.Entry<String, JsonNode>> properties = entry.getValue().fields();
            while (properties.hasNext()) {
                Map.Entry<String, JsonNode> property = properties.next();
                assertThat(actual.get(entry.getKey()))
                        .as("GitHub Action %s %s property %s", entryKind, entry.getKey(), property.getKey())
                        .containsEntry(property.getKey(), property.getValue().asText());
            }
        }
    }

    private static Map<String, Map<String, String>> parseYamlSection(String yaml, String section) {
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        boolean inSection = false;
        String currentEntry = null;
        for (String line : yaml.lines().toList()) {
            if (!inSection) {
                if (line.equals(section + ":")) {
                    inSection = true;
                }
                continue;
            }
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                break;
            }
            if (line.startsWith("  ") && !line.startsWith("    ") && line.strip().endsWith(":")) {
                currentEntry = line.strip().substring(0, line.strip().length() - 1);
                entries.put(currentEntry, new LinkedHashMap<>());
                continue;
            }
            if (currentEntry != null && line.startsWith("    ")) {
                String propertyLine = line.strip();
                int separator = propertyLine.indexOf(':');
                if (separator > 0) {
                    String property = propertyLine.substring(0, separator);
                    String value = stripYamlScalar(propertyLine.substring(separator + 1));
                    entries.get(currentEntry).put(property, value);
                }
            }
        }
        return entries;
    }

    private static String stripYamlScalar(String value) {
        String stripped = value.strip();
        if (stripped.length() >= 2
                && ((stripped.startsWith("\"") && stripped.endsWith("\""))
                || (stripped.startsWith("'") && stripped.endsWith("'")))) {
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped;
    }

    private static Class<?>[] parameterTypes(JsonNode names) throws ClassNotFoundException {
        List<Class<?>> types = new ArrayList<>();
        for (JsonNode name : names) {
            types.add(classForName(name.asText()));
        }
        return types.toArray(Class<?>[]::new);
    }

    private static Class<?> classForName(String name) throws ClassNotFoundException {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> Class.forName(name);
        };
    }

    private static String parameterNames(Class<?>[] parameters) {
        return Arrays.stream(parameters).map(Class::getName).collect(Collectors.joining(", "));
    }

    private static RuleContext context(String text) {
        return new RuleContext(List.of(), null, text);
    }

    private static Incident customIncident(String type) {
        return new CatalogIncident(
                type,
                IncidentCategory.TECHNICAL,
                Severity.MEDIUM,
                Confidence.HIGH,
                "Compatibility probe",
                "Custom deterministic rule matched",
                "Compatibility probe root cause",
                "Review the compatibility probe guidance");
    }

    private static DiagnosisEngine engine() {
        return new DiagnosisEngine(new NoopLlmClient());
    }

    private static JsonNode loadSnapshot() throws Exception {
        try (InputStream input = PublicContractCompatibilityIT.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            assertThat(input).as("public contract snapshot %s", SNAPSHOT_RESOURCE).isNotNull();
            return JSON.readTree(input);
        }
    }

    private static String usefulMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.lines().map(String::strip).filter(line -> !line.isBlank()).collect(Collectors.joining(" "));
    }

    @FunctionalInterface
    private interface CheckedContract {
        void check() throws Exception;
    }

    private static final class NoopLlmClient implements LlmClient {
        @Override
        public String explainKnownIncident(Incident incident) {
            return null;
        }

        @Override
        public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
            return null;
        }
    }
}
