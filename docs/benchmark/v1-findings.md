# Benchmark v1 — findings

Full numbers: [v1 report](v1.md) · [ranking settings](v1-tuning.md) · method and limits: [README](README.md).

## Headline

`qwen3:14b` and `bge-m3` on one RTX 3060, 90 questions in five languages:

| | |
|---|---|
| Gold article ranked **first** | **79%** (was 40% before tuning) |
| Gold article in the top 5 | 84% |
| Answers citing the gold article | 81% |
| Answers judged correct / correct or partial | 59% / 95% |
| Unanswerable questions refused | **15 / 15** |
| Gold units found in the top 5 through a text in another language | 35% |

## 1. The first hybrid ranking was worse than vector search alone

The first run fused vector and full-text search with equal weights:

| Ranking | Hit@1 | MRR@10 |
|---|---:|---:|
| Hybrid, equal weights (week 3 default) | 40% | 0.55 |
| Vector only | 68% | 0.74 |
| **Vector, recitals × 0.6 (new default)** | **79%** | **0.82** |

Two causes, both visible in the results:

- **Recitals were promoted.** Recitals paraphrase the articles they explain, so both searches find
  them and fusion counted them twice: with equal weights a recital ranked first for 33 of 75
  questions. The answer a user needs, and should cite, is the binding article. Down-weighting
  recitals is the largest single gain (+11 points Hit@1 over vector search alone).
- **Full-text search does not help on paraphrased questions.** Users write "banned", the law says
  "prohibited"; "delete my data", the law says "erasure". Multilingual embeddings bridge that, word
  matching does not. Full-text search stays available (`fontis.ask.text-weight`) for exact
  references such as article numbers, which v1 does not test.

The weights were chosen on the same 75 questions they are reported on, so the 79% is optimistic;
v2 will keep a held-out part.

## 2. Cross-lingual retrieval matters

35% of the gold units found in the top 5 matched a text in another language than the question —
an Italian question finding the French text of AI Act Article 5, for example. Searching only the
question's language would lose those.

German is the weakest language for retrieval (Hit@1 67% against 80–87% for the others).

## 3. Refusals work

All 15 unanswerable questions were refused: 10 by the similarity threshold before the model was
called (`NO_SOURCE`), 5 by the model itself (`NO_ANSWER`). No answerable question was refused.

## 4. Answers: where they fall short

| Question | Verdicts (5 languages) | Cause |
|---|---|---|
| NIS2 maximum fine for essential entities | 3 incorrect, 2 correct | **Pipeline**: Article 34 is split into three chunks; the model received only the best-matching one, which states the fine for *important* entities (EUR 7 000 000 / 1.4 %) instead of *essential* ones (EUR 10 000 000 / 2 %). |
| Deployer obligations, legal bases, user data access | gold not ranked first in 4–5 languages | **Retrieval**: a related article or recital ranks above the gold one. |
| Prohibited practices, breach notification, legal bases | partial in all 5 languages | **Long answers**: the reference lists six to eight items; answers of at most six sentences cover part of them. |
| Data Act cloud switching | 4 partial, 1 incorrect | **Judge**: the German answer was rated *incorrect* for details (14 working days, a one-off extension by the customer) that **are** in Article 25; it is really *partial*. |

Manual review of the four *incorrect* verdicts: three are real errors (the NIS2 case above), one is a
judge error. The raw verdicts with the judge's reasons are kept for this kind of check.

## Next steps

1. Give the model **every retrieved chunk of a source article**, not just the best one — the fix
   for the NIS2 error.
2. Improve German retrieval; try `Qwen3-Embedding` against `bge-m3`.
3. Dataset v2: 150 questions with a held-out part, exact-reference questions (article numbers,
   acronyms) to re-test full-text search, and questions whose answer spans two regulations.
4. A judge that sees the gold article text as well as the reference answer.
