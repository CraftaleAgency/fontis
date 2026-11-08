# Benchmark v2 — findings

Full numbers: [v2 report](v2.md) · [ranking settings](v2-tuning.md) · method and limits:
[README](README.md) · dataset: [`bench/datasets/v2.jsonl`](../../bench/datasets/v2.jsonl), also on
[Hugging Face](https://huggingface.co/datasets/Fla00/fontis-eu-law-qa).

## Headline

`qwen3:14b` and `bge-m3` on one RTX 3060, 150 questions in five languages:

| | v1.1 (90 questions) | **v2 (150 questions)** |
|---|---:|---:|
| Gold article ranked first | 79% | **78%** |
| Gold article in the top 5 | 84% | **89%** |
| Answers citing the gold article | 79% | **88%** |
| Answers judged correct / correct or partial | 67% / 97% | **64% / 96%** |
| Unanswerable questions refused | 15 / 15 | **30 / 30** |

## 1. Retrieval holds on questions it was not tuned on

The ranking weights (recitals × 0.6, no full-text weight) were chosen on the v1 questions. v2 adds 9
new answerable questions, never used for tuning:

| Questions | n | Hit@1 | Hit@5 | Answers citing gold | Correct (judge) | Correctness score |
|---|---:|---:|---:|---:|---:|---:|
| v1 questions (tuned on) | 75 | 79% | 84% | 81% | 71% | 83% |
| **New questions (held out)** | 45 | **76%** | **98%** | **98%** | 53% | 74% |

Retrieval generalises: on unseen questions the gold article is in the top 5 almost every time. The
lower answer scores on the new questions come from what they ask (section 3), not from retrieval.
The comparison of 13 ranking settings on all 120 answerable questions gives the same order as in v1:
the default is tied first with vector search, equal-weight hybrid ranking is last at 41% Hit@1
([table](v2-tuning.md)).

## 2. No invented answers, including to near-miss questions

v2 adds three unanswerable questions that sit close to the corpus: the maximum fine under the
Digital Services Act, the vulnerability reporting deadline under the Cyber Resilience Act (neither
regulation is in the corpus, but both resemble NIS2 and the GDPR) and the amount of Meta's 2023 GDPR
fine (an enforcement decision, not legislation). All 30 unanswerable questions were refused in every
language, 10 by the similarity threshold and 20 by the model itself.

## 3. Most imperfect answers are incomplete lists

Of the 120 answers, 77 were judged correct, 38 partial and 5 incorrect. Partial verdicts
concentrate on questions whose answer is a list: prohibited AI practices (8 items), obligations of
general-purpose AI providers, the NIS2 minimum security measures (10 items), the contents of a
fundamental rights impact assessment. The system prompt asks for *at most six sentences*, and the
judge marks an answer that names five of eight practices as partial. Shorter answers are a product
choice; a list-aware prompt (complete enumerations, short sentences) is the obvious next experiment.

## 4. The remaining errors are reasoning, not retrieval

The cross-regulation question (*which has higher maximum fines, the GDPR or the AI Act?*) retrieved
and cited both gold articles in all five languages. In English, Italian and German the answer is
right; in French and Spanish the model quotes the correct figures (EUR 35 million or 7 % against
EUR 20 million or 4 %) and then concludes the opposite. Asked for the maximum GDPR fine in French,
it quoted the lower tier of Article 83 (EUR 10 million or 2 %) instead of the higher one. A
14-billion-parameter model reading the right sources can still compare them wrongly: these are
the cases a larger answer model, or a check of numeric claims against the cited text, would fix.

## 5. Languages

French has the best retrieval (96% Hit@5) and the lowest correctness (54%), German the weakest
Hit@1 (67%). With 24 questions per language, differences of a few points are within noise.
