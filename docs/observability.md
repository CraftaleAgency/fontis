# Observability

Two complementary views: **metrics** in Prometheus and Grafana for the service as a whole, and
**traces** in Langfuse (or any OpenTelemetry backend) for individual questions.

## Metrics

The backend exposes Prometheus metrics on its internal management port
(`http://backend:8081/actuator/prometheus`), which is not reachable from the internet.

| Metric | Source | Meaning |
|---|---|---|
| `fontis_answers_total{status, lang}` | Fontis | Questions by outcome (`ANSWERED`, `NO_SOURCE`, `NO_ANSWER`, `UNCITED`) and language |
| `fontis_retrieval_seconds` | Fontis | Retrieval time (histogram) |
| `fontis_generation_seconds` | Fontis | Generation time when the model is called (histogram) |
| `gen_ai_client_operation_seconds{gen_ai_operation_name, gen_ai_response_model}` | Spring AI | Duration of each chat and embedding call |
| `gen_ai_client_token_usage_total{gen_ai_token_type}` | Spring AI | Input and output tokens |
| `http_server_requests_seconds`, `jvm_*` | Spring Boot | HTTP and JVM |

### Grafana dashboard

[`deploy/grafana/dashboards/fontis.json`](../deploy/grafana/dashboards/fontis.json) is provisioned
automatically with the `monitoring` profile, and can be imported into an existing Grafana:

- questions and refusal share over 24 hours, answer time p95, output tokens;
- questions per minute by outcome and by language;
- retrieval and generation time, p50 and p95;
- chat tokens per minute, mean model call duration per model;
- JVM heap, API requests by endpoint and status.

## Traces (Langfuse)

Each question is one trace, following Langfuse's
[trace best practices](https://langfuse.com/docs/observability/best-practices):

```
answer-question          chain       question → answer; status, language, sources, citations
├── retrieve-context     retriever   question → retrieved sources with similarity
│   └── embed-text       embedding   bge-m3, tokens
└── generate-answer      generation  messages sent to the model → reply; qwen3:14b, tokens
```

- **Stable names**, not named after the model, so evaluators and dashboards survive a model change.
- **Refusals stand out**: `NO_SOURCE`, `NO_ANSWER` and `UNCITED` answers have level `WARNING`.
- **Filterable on every observation**: trace name, environment (`FONTIS_ENVIRONMENT`), tags
  (`anonymous` or `signed-in`) and, for signed-in users, the internal user id (never the e-mail
  address) are copied to every span.
- **No noise**: only the spans of a question are exported. HTTP, security and database spans, Spring
  AI's chat client and advisor wrappers and the HTTP calls to Ollama stay out of Langfuse.

Spans go over OTLP/HTTP to Langfuse's `/api/public/otel/v1/traces`, with the
`x-langfuse-ingestion-version: 4` header for real-time ingestion:

```bash
FONTIS_TRACING_ENABLED=true
FONTIS_OTLP_ENDPOINT=https://<langfuse-host>/api/public/otel/v1/traces
FONTIS_OTLP_AUTHORIZATION="Basic $(printf 'pk-lf-...:sk-lf-...' | base64)"
FONTIS_ENVIRONMENT=production
```

### Content

By default traces carry the structure, timings, models, token counts and outcomes, but **not the
questions, sources or answers**. Set `FONTIS_TRACING_CAPTURE_CONTENT=true` to send them too: the
inputs and outputs above, which Langfuse needs for LLM-as-a-judge evaluations, datasets and
reviewing answers. Questions can contain personal data, so enable it only where sending them to the
tracing backend is covered by your data protection assessment, for example a self-hosted Langfuse
in the EU.

Tracing is off by default (`FONTIS_TRACING_ENABLED=false`); sampling is controlled by
`FONTIS_TRACING_SAMPLING` (default `1.0`, every question).
