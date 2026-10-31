# Roadmap

| Week | Goal | Status |
|---|---|---|
| 1 | Repository, EU legislation corpus in 5 languages, cross-lingual alignment check, backend skeleton, database schema | ✅ |
| 2 | Spring Batch ingestion: corpus JSONL → Postgres, paragraph-level chunks (units reach 17k characters), `bge-m3` embeddings in pgvector — [details](ingestion.md) | ✅ |
| 3 | `/ask` endpoint: hybrid retrieval (pgvector + full-text), cited answers, refusal when no source supports the answer — [details](answering.md) | ✅ |
| 4 | Benchmark v1 (Python): 75 questions in 5 languages, retrieval and answer metrics, first report | |
| 5 | Next.js UI in several languages, AI Act transparency features (AI disclosure, answer log, visible sources) | |
| 6 | Bring-your-own documents: upload, users, groups and per-document permissions | |
| 7 | Langfuse tracing, Grafana dashboards, public demo, Docker Compose for EU cloud deployment | |
| 8 | Benchmark v2 (150 questions) published on Hugging Face, technical write-up, release | |
