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
| **New questions (held out, 9 base questions)** | 45 | **76%** | **98%** | **98%** | 53% | 74% |

Retrieval generalises: on unseen questions the gold article is in the top 5 almost every time. The
lower answer scores on the new questions come from how much of each article reaches the model
(section 3), not from retrieval.
The comparison of 13 ranking settings on all 120 answerable questions gives the same order as in v1:
the default is tied first with vector search, equal-weight hybrid ranking is last at 41% Hit@1
([table](v2-tuning.md)).

## 2. No invented answers, including to near-miss questions

v2 adds three unanswerable questions that sit close to the corpus: the maximum fine under the
Digital Services Act, the vulnerability reporting deadline under the Cyber Resilience Act (neither
regulation is in the corpus, but both resemble NIS2 and the GDPR) and the amount of Meta's 2023 GDPR
fine (an enforcement decision, not legislation). All 30 unanswerable questions were refused in every
language, 10 by the similarity threshold and 20 by the model itself.

## 3. Partial answers: lists, and less context than expected

Of the 120 answers, 77 were judged correct, 38 partial and 5 incorrect. Partial verdicts
concentrate on questions whose answer is a list: prohibited AI practices (8 items), obligations of
general-purpose AI providers, the NIS2 minimum security measures (10 items), the contents of a
fundamental rights impact assessment.

An earlier version of this page blamed the system prompt, which asks for *at most six sentences*.
The answers do not support that: only 9 of the 38 partial answers use six sentences, and 22 use
three or fewer. A cause that the answers do show is the **context budget of 2,400 characters per
source**. Long
articles are stored as many chunks and only two or three of them reach the model: Article 5 of the
AI Act is 12 chunks and about 11,000 characters in English, so most of the prohibited practices are
never in the prompt. The model cannot list what it was not shown.

**Follow-up ([experiments](v2-context.md)):** giving the best-ranked sources up to 6,000
characters fixed the list of prohibited practices and the NIS2 measures and cut wrong answers from 5
to 2, but left 40 answers partial, most of them short summaries of articles the model had in full.
The context budget was one cause, not the main one; a prompt asking for complete lists did not help
on balance either. Run-to-run variation (10–13 verdicts change between runs) is larger than most of
these effects.

## 4. Two reasoning errors, one more context error

The cross-regulation question (*which has higher maximum fines, the GDPR or the AI Act?*) retrieved
and cited both gold articles in all five languages. In English, Italian and German the answer is
right; in French and Spanish the model quotes the correct figures (EUR 35 million or 7 % against
EUR 20 million or 4 %) and then concludes the opposite. These two are reasoning errors: a
14-billion-parameter model reading the right figures can still compare them wrongly, and a check of
numeric claims against the cited text would catch them.

Asked for the maximum GDPR fine in French, Fontis quoted the lower tier of Article 83 (EUR 10
million or 2 %). An earlier version of this page counted that as a reasoning error too. It is a
context error: the French text of Article 83 is six chunks, the higher tier (EUR 20 million or 4 %)
is in the fourth and fifth, and a replay of the context assembly shows the model received the
second and third. It answered correctly from what it was given. (With the larger context of the
[follow-up experiments](v2-context.md), the model sees the higher tier and still quotes the lower
one, so this case is both.)

## 5. Languages

French has the best retrieval (96% Hit@5) and the lowest correctness (54%), German the weakest
Hit@1 (67%). With 24 questions per language, differences of a few points are within noise.

## 6. Limits of these numbers

- **The judge makes mistakes.** A manual re-read of the verdicts found `gemma4:12b` wrong in
  roughly one case in seven, in both directions; graded by hand, about 69% of the answers are
  correct rather than 64%. That re-read was done by one person and is an estimate, so the tables
  keep the judge's verdicts. A second judge and a measured judge–human agreement are planned.
- **The effective sample is 24, not 120.** The five languages are translations of the same 24
  answerable base questions, so the answers are not independent. Confidence intervals should come
  from a bootstrap paired by base question; until then, treat differences of several points as noise.
- **Correct is not always supported.** A few answers are right while the cited source does not
  contain the claim (the legal bases of the GDPR, in Spanish and Italian): the model answered from
  memory. The benchmark does not yet measure whether each sentence is supported by its citation.
- **Source language leaks.** Sources are given in the language that matched, and words from it
  occasionally appear in the answer (a Spanish legal term in a French answer).
