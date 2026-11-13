# Benchmark v2 — context and prompt experiments

The [v2 findings](v2-findings.md) traced several partial answers to the context budget: each of the
six sources reached the model as at most 2,400 characters of its article, and long articles such as
AI Act Article 5 (about 11,000 characters) lost most of their content. Three variants were run on
the same 150 questions, with the same retrieval (`bench/datasets/v2.jsonl`), the same answer model
(`qwen3:14b`) and the same judge (`gemma4:12b`, temperature 0).

| Run | Context per source, by rank | Prompt |
|---|---|---|
| v2 | 2,400 each | at most six sentences |
| context-a | 9,000 for the first, 1,500 for the others | same |
| **context-b** (new default) | 6,000 / 3,000 / 3,000 / 1,200 … | same |
| prompt | as context-b | complete bullet lists; figures before conclusions |

In every run the first source's chunks are chosen as before (the retrieved chunks first), plus the
article's first chunk, which often holds the lead-in of a list ("The following AI practices shall be
prohibited:"). No prompt exceeded the 8,192-token context window.

## Results

On the 120 answerable questions:

| Run | Correct | Partial | Incorrect | Score | Answers citing gold | Refused (of 30) | Generation median / p90 |
|---|---:|---:|---:|---:|---:|---:|---:|
| v2 | 77 | 38 | 5 | 80% | 88% | 30 | 8.5 s / 26.9 s |
| context-a | 77 | 42 | 1 | 82% | 88% | 30 | 6.5 s / 11.6 s |
| **context-b** | **78** | 40 | **2** | **82%** | 88% | 30 | 7.0 s / 12.0 s |
| prompt | 79 | 39 | 2 | 82% | 82% | 30 | 6.6 s / 13.7 s |

Score counts correct as 1 and partial as 0.5. The v2 latency p90 includes a cold model start.

Paired by base question (24 answerable base questions, bootstrap over base questions, 95%
intervals), score change against v2:

| Run | Change | 95% interval |
|---|---:|---:|
| context-a | +1.7 points | −3.8 … +7.1 |
| context-b | +1.7 points | −2.5 … +6.7 |
| prompt (against context-b) | +0.4 points | −5.4 … +5.8 |

## What this shows

- **Larger context helps where it was expected to, and little elsewhere.** The list questions
  improve — prohibited AI practices +0.2, NIS2 security measures +0.4 on a 0–1 scale — and wrong
  answers fall from 5 to 2. On average the change is within noise.
- **One context error turned out to be a model error too.** For the maximum GDPR fine in French,
  the model now receives Article 83 almost whole, including the higher tier (EUR 20 million or 4 %),
  and still quotes the lower one (EUR 10 million or 2 %) in every run. Missing context explained
  the v2 answer; it does not explain this one.
- **Giving only the first source a large budget hurts comparisons.** In context-a, sources ranked
  second or third received 1,500 characters; the German GDPR–AI Act comparison then saw only the
  lower GDPR tier. context-b keeps 3,000 characters for the second and third sources.
- **The context was not the main cause of partial answers after all.** With whole articles in the
  prompt, 40 answers are still partial and 23 of them use three sentences or fewer: the model
  summarises rather than enumerates. The [findings](v2-findings.md) said the opposite; this run
  corrects them.
- **Asking for complete lists did not help on balance.** It fixed the GDPR–AI Act comparison and
  the list of prohibited practices, broke others, and lowered the share of answers citing a gold
  article from 88% to 82%. It was not kept.
- **Run-to-run variation is large.** Between two runs, 10 to 13 answers move from correct to
  partial and as many move back. With 24 base questions, this benchmark cannot detect effects of a
  couple of points; a larger and more varied dataset (v3) is needed before smaller changes can be
  measured.

context-b is the new default (`fontis.ask.source-chars`): it is at least as good as v2 on every
measure here, has fewer wrong answers, and answers faster because the model reads one long source
instead of many fragments.

Raw results: `data/results/v2-context`, `v2-context-b`, `v2-prompt` (not in git; rerun with
`fontis-bench eval --run <name>` and `FONTIS_SOURCE_CHARS`).
