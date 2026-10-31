# Benchmark

A multilingual benchmark for retrieval and cited answers on EU legislation.

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

```bash
cd bench
uv run fontis-bench eval check                  # dataset sanity
uv run fontis-bench eval retrieval              # /api/search in hybrid, vector and text modes
uv run fontis-bench eval answers                # /api/ask
uv run fontis-bench eval judge --ollama http://<host>:11434 --model gemma4:12b
uv run fontis-bench eval report                 # docs/benchmark/<run>.md and <run>-summary.json
uv run fontis-bench eval --run v1-tuning tune   # compare ranking settings
```

Every phase resumes where it stopped. Answer and judge phases are run one after the other so that
a single 12 GB GPU never has to hold both chat models at once.

## Limits

- **Small.** 15 answerable questions × 5 languages. Differences of a few points are noise.
- **Tuned and evaluated on the same questions.** The ranking weights were chosen on this set; v2
  (150 questions, week 8) will keep a held-out part.
- **Strict gold.** Only the article that states the rule counts. A recital explaining the same rule
  is a miss for retrieval metrics, although it may still support a correct answer.
