# Fontis

**EU-sovereign document AI with cited answers.** Ask questions about your organisation's documents in any
European language and get answers that cite the exact source — with data and models under your control.

> 🚧 Early development — see the [roadmap](docs/roadmap.md).

## Why

- **Cited or nothing.** Every answer points to the article, paragraph and document it comes from.
  When no source supports an answer, Fontis says so instead of guessing.
- **Multilingual by design.** Ask in Italian, find the answer in a German document, read it in Italian.
- **Sovereign.** Runs on your own servers or on EU clouds, with local open-weight models or an EU provider
  (Mistral). Documents never have to leave the EU.
- **AI Act–ready.** AI disclosure, answer logs and visible sources are built in, not bolted on.
- **Measured.** A public, multilingual benchmark shows how well it works — and on your own documents too.

## Architecture

| Part | Technology |
|---|---|
| Backend and ingestion | Java 21, Spring Boot 4, Spring AI 2, Spring Batch |
| Retrieval | PostgreSQL 17 + pgvector, hybrid vector and full-text search |
| Models | Ollama (e.g. `qwen3:14b`, `bge-m3`); Mistral as EU cloud option (planned) |
| Benchmark and data tooling | Python 3.12, uv |

## Demo corpus

The EU AI Act, GDPR, Data Act and NIS2 in English, Italian, German, French and Spanish:
**4,700 citable units**, aligned across languages. Downloaded from the Publications Office of the EU — see
[data sources](docs/data-sources.md).

## Quick start

Requirements: Docker, Java 21, [uv](https://docs.astral.sh/uv/), and Ollama for the models.

```bash
# 1. Corpus: download from CELLAR, split into units, check language alignment
cd bench
uv run fontis-bench corpus fetch
uv run fontis-bench corpus build
uv run fontis-bench corpus check

# 2. Database
cd .. && docker compose up -d

# 3. Backend (tests use Testcontainers)
cd backend && ./mvnw test && ./mvnw spring-boot:run
```

## Repository layout

```
backend/   Spring Boot service: ingestion, retrieval, answers
bench/     Python: corpus tooling and multilingual benchmark
docs/      Roadmap, data sources, design notes
```

## License

[Apache-2.0](LICENSE). The demo corpus is © European Union and is not redistributed here — see [NOTICE](NOTICE).
