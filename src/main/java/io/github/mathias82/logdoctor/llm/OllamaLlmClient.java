package io.github.mathias82.logdoctor.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import io.github.mathias82.logdoctor.core.Incident;

import java.time.Duration;
import java.util.Map;

public class OllamaLlmClient implements LlmClient {

    private static final String API_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "qwen2.5:3b";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(60))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String explainKnownIncident(Incident incident) {
        return callOllama(LlmPrompts.knownIncidentPrompt(incident));
    }

    @Override
    public String analyzeUnknownLog(String rawLog) {
        return callOllama(LlmPrompts.unknownLogPrompt(rawLog));
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

            RequestBody body = RequestBody.create(
                    mapper.writeValueAsString(payload),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String json = response.body().string();
                return mapper.readValue(json, OllamaResponse.class).response();
            }

        } catch (Exception e) {
            return "LLM analysis failed: " + e.getMessage();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaResponse(String response) {}
}
