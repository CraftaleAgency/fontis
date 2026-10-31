# Answering

`POST /api/ask` answers a question from the corpus only, citing the units it relies on — or says
that it cannot.

```
question ──► hybrid retrieval ──► similarity guard ──► qwen3:14b with numbered sources ──► citations
```

## API

```bash
curl -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question": "Ho il diritto di far cancellare i miei dati personali?", "lang": "ita"}'
```

`lang` (`eng`, `ita`, `deu`, `fra`, `spa`; default `eng`) sets the language of source labels and
EUR-Lex links. The model answers in the language of the question.

| Field | Meaning |
|---|---|
| `status` | `ANSWERED`, `NO_SOURCE` (retrieval found nothing close enough; the model was not called), `NO_ANSWER` (the model found the sources insufficient), `UNCITED` (an answer without citations, not to be trusted) |
| `answer` | Text with `[n]` citation markers |
| `citations` | Sources cited in the answer, with document, label, heading and EUR-Lex URL |
| `sources` | Every source given to the model |
| `retrievalMs`, `generationMs` | Timings |

`GET /api/search?q=...&lang=...&limit=...` returns retrieval results only. The benchmark uses it to
measure retrieval separately from generation.

## Retrieval

1. **Vector search**: the 40 chunks closest to the question embedding (`bge-m3`, cosine, HNSW).
2. **Full-text search** (optional): the 40 best chunks for the question as an OR query, each chunk
   stemmed with its own language, ranked by **how many distinct question terms** they contain, then
   by term density.
3. **Weighted reciprocal rank fusion** (k = 60) merges both lists.
4. **One result per unit**: an article usually matches in several languages; only its best chunk is
   kept, and it is labelled and linked in the requested language.
5. **Recitals × 0.6**, so the binding article ranks above the recital that explains it.

The weights come from the [benchmark](benchmark/v1-findings.md): equal-weight fusion ranked the
gold article first for 40% of the questions, vector search with down-weighted recitals for 79%.
Full-text search did not help on paraphrased questions, so its weight is 0 by default
(`fontis.ask.text-weight`) and the search is skipped; `GET /api/search` accepts `mode`,
`textWeight`, `recitalWeight` and `sumLanguages` to compare settings.

## Refusing instead of guessing

- If the closest chunk has a cosine similarity below **0.45**, the response is `NO_SOURCE` and the
  model is not called.
- Otherwise the model receives up to six numbered sources — each up to 2,400 characters of its
  article, not just the best chunk — and must end every factual sentence with
  `[n]`, use nothing else, and reply `NO_ANSWER` when the sources do not contain the answer.
- Citation markers that point to no source are dropped; an answer with none is `UNCITED`.

Thinking is disabled for `qwen3` and any `<think>` block is stripped.

## First results

`qwen3:14b` and `bge-m3` on one RTX 3060, reached over Tailscale:

| Question | Status | Cited |
|---|---|---|
| *Quali pratiche di intelligenza artificiale sono vietate?* (IT) | ANSWERED | AI Act Articolo 5 — matched in the **French** text — and recital 28 |
| *Ho il diritto di far cancellare i miei dati personali? Entro quanto tempo?* (IT) | ANSWERED | GDPR Articolo 17, recitals 65 and 68 — "senza ingiustificato ritardo" |
| *Within how many hours must an essential entity give an early warning of a significant incident?* (EN) | ANSWERED | NIS2 recital 102 — "within 24 hours" |
| *¿Puedo cambiar de proveedor de servicios en la nube y en cuánto tiempo?* (ES) | ANSWERED | Data Act Artículos 23 and 25, recitals 87 and 95 |
| *Welche Pflichten haben Betreiber von Hochrisiko-KI-Systemen?* (DE) | ANSWERED | AI Act Artikel 26, recitals 91 and 72 |
| *Chi ha vinto i mondiali di calcio del 2006?* (IT) | NO_SOURCE | — model not called |
| *Quanto costa la licenza di Microsoft Copilot per un'azienda?* (IT) | NO_ANSWER | — AI Act sources found, judged insufficient |

Retrieval takes 0.4–1 s (mostly the embedding call), generation 2–12 s once the model is loaded.

Known weaknesses, to be measured by the benchmark: the NIS2 answer cites a recital rather than
Article 23, which states the same rule; the Spanish answer on switching stays vague about the
deadlines in Article 25.
