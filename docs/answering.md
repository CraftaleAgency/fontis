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
| `verification` | Each sentence checked against the cited text — see [Checking each sentence](#checking-each-sentence); `null` when the answer was not checked |
| `missing` | What the sources do not say, when the model answered only part of the question (`fontis.ask.features.partial-answers`); otherwise `null` |
| `citations[].appliesFrom`, `.applicabilityNote` | From when the cited unit applies, and a note when that changed (e.g. a later deferral) |

`GET /api/search?q=...&lang=...&limit=...` returns retrieval results only. The benchmark uses it to
measure retrieval separately from generation and to compare ranking settings; it is open to
administrators only.

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
- Otherwise the model receives up to six numbered sources — several chunks of each article, not
  just the best one: up to 6,000 characters for the best-ranked source, 3,000 for the next two and
  1,200 for the rest (`fontis.ask.source-chars`, [experiments](benchmark/v2-context.md)) — and must
  end every factual sentence with
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

## Checking each sentence

After generation, `AnswerVerifier` checks every sentence of the answer against the text the model
was given for the sources it cites. One embedding call per answer; the answer itself is not changed.

1. **Sentences.** The answer is split into sentences; each line of a list is one, and a list item
   without citations takes those of its lead-in ("The following practices are prohibited [1]:").
2. **Passage.** Among the lines of the cited sources' text, the one closest to the sentence by
   `bge-m3` similarity. Being multilingual, it compares an Italian sentence with a French passage.
3. **Pinpoint.** The passage's place in its article, from the paragraph and point markers of the
   unit (`1.`, `(a)`, `a)`, `(i)`), which are the same in every language: "Articolo 5(1)(c)". The
   passage is returned in the answer language when the unit exists in it.
4. **Figures.** Every amount, percentage, period and year in the sentence must appear in the cited
   text or in the question, however written ("20 000 000 EUR" = "20 milioni di euro"). Numbers in
   references ("Article 83(5)", "2016/679") and numbers below 10 (often written in words in the law)
   are not checked; nor are numbers in lists of references ("Articles 111 and 113").

| Verdict | Meaning |
|---|---|
| `SUPPORTED` | similarity ≥ `fontis.verify.supported` (0.55) |
| `WEAK` | similarity ≥ `fontis.verify.weak` (0.45) |
| `UNSUPPORTED` | no cited passage is close |
| `FIGURE_MISMATCH` | a figure is not in the cited text — the most harmful error found so far (v2: the lower GDPR fine tier quoted for the higher one) |
| `UNCITED` | the sentence cites nothing |

The web app shows the summary ("4 of 5 sentences match the cited text") and each sentence with its
passage, open by default when a figure is missing or a sentence is unsupported. When a passage of the
cited text contains every figure of the sentence, it is chosen over closer ones (GDPR Article 83(4)
and 83(5) read alike but state different fines). Prometheus counts
`fontis_answer_sentences_total` by verdict.

**Measured on benchmark v2.1** ([findings](benchmark/v2.1-findings.md)): the figure check caught
two real wrong citations; embedding similarity, however, does **not** tell supported sentences from
unsupported ones (median 0.79 against 0.77, judged by `fontis-bench eval support`). Similarity is
used to locate the passage and its pinpoint; the low thresholds only flag sentences far from every
cited passage. Judging support needs an entailment model, not yet added. The figure check is exact,
but it only catches a figure that is absent from the cited text, not one taken from the wrong
paragraph of it.

## Optional context and prompt features

Off by default until measured on the benchmark, except the dates (`fontis.ask.features`):

| Setting | Default | Effect |
|---|---|---|
| `applicability` (`FONTIS_APPLICABILITY`) | on | Today's date at the top of the prompt and "(Applies from 2026-08-02. …)" under each source with a date, so "does this already apply?" has an answer. Dates come from the corpus build (`bench/src/fontis_bench/applicability.py`). |
| `references` (`FONTIS_FOLLOW_REFERENCES`) | 0 | Up to this many units that the best source refers to (e.g. Annex III from Article 6) added as extra numbered sources, 1,200 characters each. |
| `definition-chars` (`FONTIS_DEFINITION_CHARS`) | 0 | Definitions of the terms the sources use, from the act's definitions article (AI Act Article 3, GDPR Article 4…), added as one extra source within this many characters, most specific terms first. |
| `partial-answers` (`FONTIS_PARTIAL_ANSWERS`) | off | The model may answer the covered part and end with `NOT_IN_SOURCES: …`, returned as `missing`. |

Extra sources lengthen the prompt: with all of them on, check that it still fits `num-ctx` (the
backend logs a warning when it may not).
