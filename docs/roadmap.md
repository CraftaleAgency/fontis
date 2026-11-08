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
