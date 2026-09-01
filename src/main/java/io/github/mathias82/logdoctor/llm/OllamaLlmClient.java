package io.github.mathias82.logdoctor.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.engine.LogRedactor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.Duration;
import java.util.Map;

public class OllamaLlmClient implements LlmClient {

    static final String DEFAULT_BASE_URL = "http://localhost:11434";
    static final String DEFAULT_MODEL = "qwen2.5:3b";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final LogRedactor redactor;
    private final String apiUrl;
    private final String model;

    public OllamaLlmClient() {
        this(
                setting("log.doctor.ollama.url", "LOG_DOCTOR_OLLAMA_URL", DEFAULT_BASE_URL),
                setting("log.doctor.ollama.model", "LOG_DOCTOR_OLLAMA_MODEL", DEFAULT_MODEL)
        );
    }

    public OllamaLlmClient(String baseUrl, String model) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .callTimeout(Duration.ofSeconds(60))
                .build();
        this.mapper = new ObjectMapper();
        this.redactor = new LogRedactor();
        this.apiUrl = normalizeBaseUrl(baseUrl) + "/api/generate";
        this.model = requireSetting(model, "Ollama model");
    }

    @Override
    public String explainKnownIncident(Incident incident) {
        return callOllama(redactor.redact(LlmPrompts.knownIncidentPrompt(incident)));
    }

    @Override
    public String analyzeUnknownLog(String rawLog, IncidentCategory category) {
        return callOllama(redactor.redact(LlmPrompts.unknownLogPrompt(rawLog, category)));
    }

    private String callOllama(String prompt) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.1,
                            "top_p", 0.9,
                            "num_predict", 500,
                            "repeat_penalty", 1.1
                    )
            );

            RequestBody body = RequestBody.create(mapper.writeValueAsString(payload), JSON);
            Request request = new Request.Builder().url(apiUrl).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Ollama returned HTTP " + response.code());
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IllegalStateException("Ollama returned an empty response body");
                }
                OllamaResponse parsed = mapper.readValue(responseBody.string(), OllamaResponse.class);
                if (parsed.response() == null || parsed.response().isBlank()) {
                    throw new IllegalStateException("Ollama returned no analysis");
                }
                return parsed.response().trim();
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Local Ollama analysis is unavailable", e);
        }
    }

    private static String setting(String systemProperty, String environmentVariable, String fallback) {
        String propertyValue = System.getProperty(systemProperty);
        if (propertyValue != null && !propertyValue.isBlank()) return propertyValue.trim();
        String environmentValue = System.getenv(environmentVariable);
        if (environmentValue != null && !environmentValue.isBlank()) return environmentValue.trim();
        return fallback;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = requireSetting(baseUrl, "Ollama URL");
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String requireSetting(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaResponse(String response) {}
}
