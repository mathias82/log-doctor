# Real Ollama end-to-end test

This integration test exercises the complete local-LLM path with a real Ollama container and real model inference.

## What it verifies

The test starts Log Doctor's embedded HTTP server with the real `OllamaLlmClient`, posts an unknown JVM failure to `/api/analyze/batch`, and verifies that the response contains one `UNKNOWN_FAILURE` incident with `llmUsed=true`.

## Run locally

Start Ollama and pull a lightweight integration model:

```bash
OLLAMA_MODEL=qwen2.5:0.5b docker compose -f compose.ollama.yml up -d
```

Wait until the model is available:

```bash
curl http://localhost:11434/api/tags
```

Run the real integration test:

```bash
LOG_DOCTOR_OLLAMA_MODEL=qwen2.5:0.5b mvn -Pollama-it verify
```

Then clean up:

```bash
docker compose -f compose.ollama.yml down -v
```

## Runtime overrides

Log Doctor keeps its normal defaults (`http://localhost:11434` and `qwen2.5:3b`) but can be overridden with:

- `LOG_DOCTOR_OLLAMA_URL`
- `LOG_DOCTOR_OLLAMA_MODEL`
- Java system property `log.doctor.ollama.url`
- Java system property `log.doctor.ollama.model`

The GitHub Actions workflow `.github/workflows/ollama-e2e.yml` uses `qwen2.5:0.5b` so the real Docker/model test remains practical for CI.
