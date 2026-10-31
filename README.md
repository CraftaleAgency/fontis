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

## Benchmark v1

90 questions in five languages on the AI Act, GDPR, Data Act and NIS2, with `qwen3:14b` and `bge-m3`
on a single RTX 3060:

| | |
|---|---|
| Gold article ranked first | **79%** — up from 40% with the first ranking |
| Answers citing the gold article | 79% |
| Answers correct / correct or partial (LLM judge) | **67%** / 97% — up from 59% with several chunks per source |
| Unanswerable questions refused | **15 / 15** |
| Gold articles found through a text in another language | 35% |

What the benchmark found and how it changed the ranking: [findings](docs/benchmark/v1-findings.md).

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

# 3. Backend: tests (Testcontainers), then load and embed the corpus with bge-m3
cd backend && ./mvnw test
OLLAMA_BASE_URL=http://localhost:11434 ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest
```

On one RTX 3060 the ingestion embeds 9,462 chunks in about 6.5 minutes — see [ingestion](docs/ingestion.md).

## Ask

```bash
OLLAMA_BASE_URL=http://localhost:11434 ./mvnw spring-boot:run

curl -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question": "Quali pratiche di intelligenza artificiale sono vietate?", "lang": "ita"}'
```

The answer cites AI Act **Articolo 5** — found in the French text, linked in Italian. A question the
corpus cannot answer gets `NO_SOURCE` or `NO_ANSWER` instead of a guess. See [answering](docs/answering.md).

## Repository layout

```
backend/   Spring Boot service: ingestion, retrieval, answers
bench/     Python: corpus tooling, benchmark dataset and evaluation
docs/      Roadmap, data sources, design notes
```

## License

[Apache-2.0](LICENSE). The demo corpus is © European Union and is not redistributed here — see [NOTICE](NOTICE).
