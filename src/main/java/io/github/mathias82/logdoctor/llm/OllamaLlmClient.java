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

    private static final String API_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "qwen2.5:3b";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(60))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final LogRedactor redactor = new LogRedactor();

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
                    "model", MODEL,
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
            Request request = new Request.Builder().url(API_URL).post(body).build();

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaResponse(String response) {}
}
