# Ingestion

The `ingestCorpus` Spring Batch job loads the corpus produced by `fontis-bench corpus build` into
Postgres and embeds it.

```
documents.json  ──► documentsStep ──► source_document
*.jsonl         ──► unitsStep     ──► source_unit      (articles, recitals, annexes)
source_unit     ──► chunksStep    ──► chunk            (text + bge-m3 embedding + full-text vector)
```

## Steps

| Step | What it does |
|---|---|
| `documentsStep` | Upserts the four documents from `documents.json`. |
| `unitsStep` | Reads every `<celex>.<lang>.jsonl` file and upserts the units, 500 per transaction. |
| `chunksStep` | Reads only the units that have **no chunks yet**, splits them, embeds every chunk of 16 units in one model call, and upserts the chunks. |

Because every step upserts and `chunksStep` skips units already done, the job can be re-run at
any time: after a failure it resumes where it stopped, and on a complete corpus it does nothing.

## Chunking

Units range from 140 to 17,000 characters. `Chunker` packs paragraph lines into chunks of at most
1,200 characters and never cuts a line, except a single line longer than the limit, which is split
at sentence boundaries. Each chunk keeps its unit, so an answer can always cite the exact article.

Before embedding, each chunk is prefixed with a context line such as
`AI Act — Article 5 — Prohibited AI practices`, which the chunk text alone often lacks.

The `chunk` table also stores a full-text vector built with the stemmer of the chunk's language
(`english`, `italian`, `german`, `french`, `spanish`), ready for hybrid search.

## Run

```bash
docker compose up -d
cd backend
OLLAMA_BASE_URL=http://<ollama-host>:11434 ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest
```

The `ingest` profile runs the job without starting the web server and exits when it finishes.

## Measured run

On a single RTX 3060 (12 GB) running Ollama with `bge-m3` (F16), reached over Tailscale:

| | |
|---|---|
| Units | 4,700 (4 documents × 5 languages) |
| Chunks | 9,462 — on average 820 characters; the longest AI Act article becomes 19 chunks |
| Embedding throughput | ~26 chunks/s |
| Total job time | 6 min 31 s |
| Table size | 142 MB including HNSW and full-text indexes |

## First cross-lingual check

Questions in one language, search restricted to documents in another (cosine similarity):

| Question | Searched in | Top result |
|---|---|---|
| *Quali pratiche di intelligenza artificiale sono vietate?* (IT) | German | AI Act, Artikel 5 — Verbotene Praktiken im KI-Bereich (0.680) |
| *Ho il diritto di far cancellare i miei dati personali?* (IT) | German | GDPR, Artikel 17 — Recht auf Löschung (0.700) |
| *¿Puedo cambiar de proveedor de servicios en la nube?* (ES) | Italian | Data Act, Articolo 25 — Clausole contrattuali relative al passaggio (0.593) |
| *Entro quanto tempo va notificato un incidente di sicurezza informatica?* (IT) | English | AI Act, Article 73 — Reporting of serious incidents (0.663); NIS2 Article 23 second (0.660) |

The last one shows why week 3 adds full-text search and re-ranking: NIS2 Article 23 is the better
answer but scores just below a related AI Act article.
