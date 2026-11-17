# Roadmap

| Week | Goal | Status |
|---|---|---|
| 1 | Repository, EU legislation corpus in 5 languages, cross-lingual alignment check, backend skeleton, database schema | ✅ |
| 2 | Spring Batch ingestion: corpus JSONL → Postgres, paragraph-level chunks (units reach 17k characters), `bge-m3` embeddings in pgvector — [details](ingestion.md) | ✅ |
| 3 | `/ask` endpoint: hybrid retrieval (pgvector + full-text), cited answers, refusal when no source supports the answer — [details](answering.md) | ✅ |
| 4 | Benchmark v1 (Python): 75 questions in 5 languages, retrieval and answer metrics, first report — [findings](benchmark/v1-findings.md) | ✅ |
| 5 | Next.js UI in several languages, AI Act transparency features (AI disclosure, answer log, visible sources) — [details](transparency.md) | ✅ |
| 6 | Bring-your-own documents: upload, users, groups and per-document permissions — [details](documents-and-access.md) | ✅ |
| 7 | Docker Compose for EU deployment, Prometheus and Grafana dashboard, Langfuse tracing, rate limiting; public demo prepared, not yet published — [deployment](deployment.md), [observability](observability.md) | ✅ |
| 8 | Benchmark v2: 150 questions with a held-out part, published on [Hugging Face](https://huggingface.co/datasets/Fla00/fontis-eu-law-qa) — [findings](benchmark/v2-findings.md); [technical write-up](write-up.md); release v0.1.0 | ✅ |

## Next: v0.2.0 — EU digital law, with the proof of each answer

On the `next-steps` branch. Built, tested (71 backend tests, 16 bench tests) and run locally
against the full corpus on 17 November 2026; benchmark results below once measured.

| Goal | Status |
|---|---|
| Corpus on Hugging Face: aligned units in 5 languages with references and dates — `fontis-bench corpus export`, [card](../bench/huggingface/corpus/README.md) | exported (7,840 units), not uploaded |
| MCP server: search EU law with citations from any assistant — [details](mcp.md) | runs locally; not yet public |
| Each answer sentence checked against the cited text: pinpoint to paragraph and point, passage shown, figures checked — [details](answering.md#checking-each-sentence) | written, thresholds not calibrated |
| Benchmark: share of sentences supported by the cited text (`eval support`) | written, not run |
| Partial refusals ("the sources do not say …") | written, off by default |
| Date of application of each act and article, today's date in the prompt; Digital Omnibus deferral noted | written; Omnibus dates to check against the Official Journal |
| Definitions and referenced units as extra sources | written, off by default |
| DORA, Cyber Resilience Act, Digital Services Act; dataset v2.1 with their two former trap questions | fetched, aligned in 5 languages, ingested |

Postponed: SSO, OCR, connectors, national transpositions of NIS2, the 24 official languages.
