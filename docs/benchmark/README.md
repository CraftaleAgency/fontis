# Benchmark

A multilingual benchmark for retrieval and cited answers on EU legislation.

## Dataset v2

[`bench/datasets/v2.jsonl`](../../bench/datasets/v2.jsonl), also published on Hugging Face as
[`Fla00/fontis-eu-law-qa`](https://huggingface.co/datasets/Fla00/fontis-eu-law-qa): **150 questions**,
30 base questions asked in English, Italian, German, French and Spanish. It contains v1 plus:

- 9 new answerable questions, **never used for tuning**: obligations of general-purpose AI
  providers, AI Act application dates, fundamental rights impact assessment, GDPR consent, child
  consent and impact assessment, NIS2 security measures, Data Act switching charges, and one
  question comparing two regulations (GDPR and AI Act fines, two gold articles);
- 3 new unanswerable questions close to the corpus: the Digital Services Act and the Cyber
  Resilience Act (not in the corpus) and the amount of a specific GDPR fine (an enforcement
  decision, not legislation).

Results: [v2 report](v2.md) · [findings](v2-findings.md) · [ranking settings](v2-tuning.md).

## Dataset v1

[`bench/datasets/v1.jsonl`](../../bench/datasets/v1.jsonl): **90 questions** —
15 answerable questions and 3 the corpus cannot answer, each asked in English, Italian, German,
French and Spanish.

- Questions are phrased the way a user would ask them ("Can I ask a company to delete my personal
  data?"), not copied from article titles, so that keyword matching gets no free ride.
- Each answerable question has **gold units** — the article(s) that answer it — identified by CELEX
  number and unit id, the same in every language. A retrieved unit counts whatever language it
  matched in.
- Each answerable question has an English **reference answer**, used only by the judge.
- Before writing a reference, its key facts (amounts, deadlines, obligations) were checked against
  the text of the gold article; `fontis-bench eval check` verifies that every gold unit exists.
- Unanswerable questions: a software licence price, a football result, the Italian corporate tax
  rate. The right outcome is a refusal (`NO_SOURCE` or `NO_ANSWER`).

## Metrics

| Metric | Meaning |
|---|---|
| Hit@k | A gold unit is among the first *k* results |
| MRR@10 | Mean of 1 / rank of the first gold unit (0 if not in the first 10) |
| Recall@5 | Share of gold units in the first 5 (relevant when a question has two) |
| Answered | Share of answerable questions answered with citations (`ANSWERED`) |
| Cites gold unit | The answer cites at least one gold unit |
| Correct (judge) | The judge rates the answer *correct* against the reference |
| Correctness score | correct = 1, partial = 0.5, incorrect or not answered = 0 |
| Correct refusals | Unanswerable questions answered with `NO_SOURCE` or `NO_ANSWER` |

## Judge

Answers are graded by **a different model from the one that answers** (`gemma4:12b` judging
`qwen3:14b`), with a rubric: *correct* states the key facts of the reference without
contradicting it; *partial* is relevant and not wrong but misses key facts; *incorrect* contradicts
it or misses the point.

An LLM judge is a proxy, not ground truth. The raw verdicts and reasons are kept in
`data/results/<run>/judge.jsonl` so they can be checked by hand.

## Running it

The retrieval and tuning steps call `/api/search`, which is for administrators: set
`FONTIS_ADMIN_EMAIL` and `FONTIS_ADMIN_PASSWORD` and the benchmark signs in.

```bash
cd bench
uv run fontis-bench eval check                  # dataset sanity (v2 by default; --dataset, --run)
uv run fontis-bench eval retrieval              # /api/search in hybrid, vector and text modes
uv run fontis-bench eval answers                # /api/ask
uv run fontis-bench eval judge --ollama http://<host>:11434 --model gemma4:12b
uv run fontis-bench eval report                 # docs/benchmark/<run>.md and <run>-summary.json
uv run fontis-bench eval --run v1-tuning tune   # compare ranking settings
```

Every phase resumes where it stopped. Answer and judge phases are run one after the other so that
a single 12 GB GPU never has to hold both chat models at once.

## Limits

- **Small.** 24 answerable questions × 5 languages in v2. Differences of a few points are noise.
- **Partly tuned on.** The ranking weights were chosen on the v1 questions; the 9 new questions of
  v2 are held out and reported separately in the [findings](v2-findings.md).
- **Written by the authors of the system**, from the text of the regulations. Independent
  questions would make a stronger test.
- **Point in time.** References follow the texts as published in the Official Journal and
  downloaded from EUR-Lex; later amendments are not reflected.
- **Strict gold.** Only the article that states the rule counts. A recital explaining the same rule
  is a miss for retrieval metrics, although it may still support a correct answer.
