---
license: cc-by-4.0
language:
  - en
  - it
  - de
  - fr
  - es
pretty_name: Fontis EU Law QA
size_categories:
  - n<1K
task_categories:
  - question-answering
tags:
  - legal
  - eu-law
  - rag
  - retrieval
  - multilingual
  - ai-act
  - gdpr
configs:
  - config_name: default
    data_files:
      - split: test
        path: test.jsonl
---

# Fontis EU Law QA

A small multilingual benchmark for **retrieval-augmented question answering on EU legislation**,
with **cited sources** and **questions that should be refused**.

- **150 questions**: 30 base questions, each asked in English, Italian, German, French and Spanish.
- **120 answerable** questions, each with its **gold article(s)** — identified by CELEX number and
  article id, identical in every language — and an English **reference answer**.
- **30 unanswerable** questions, some deliberately close to the corpus (regulations that are not in
  it, a specific enforcement decision). The right outcome is a refusal.
- Questions are phrased the way people ask them ("Can I ask a company to delete my personal
  data?"), not copied from article titles.

It was built for [Fontis](https://github.com/CraftaleAgency/fontis), an open-source, EU-hosted
document assistant that answers only from cited sources, but it can evaluate any RAG system over
the same four regulations.

## Corpus

The questions cover four regulations, in the texts published in the Official Journal:

| CELEX | Act |
|---|---|
| `32024R1689` | Artificial Intelligence Act — Regulation (EU) 2024/1689 |
| `32016R0679` | General Data Protection Regulation — Regulation (EU) 2016/679 |
| `32022L2555` | NIS2 Directive — Directive (EU) 2022/2555 |
| `32023R2854` | Data Act — Regulation (EU) 2023/2854 |

The texts are not included. They are available from [EUR-Lex](https://eur-lex.europa.eu) in every
EU language; the Fontis repository has the tooling that downloads them and splits them into
articles, recitals and annexes with ids that match across languages (`art_5`, `rct_29`, `anx_III`).

## Fields

| Field | Description |
|---|---|
| `id` | `<base_id>.<lang>` |
| `base_id` | Same for the five language versions of a question |
| `lang` | ISO 639-2: `eng`, `ita`, `deu`, `fra`, `spa` |
| `question` | The question, in `lang` |
| `answerable` | Whether the four regulations answer it |
| `gold` | Articles that answer it: `[{"celex": "32016R0679", "unit_id": "art_33"}]`; empty if unanswerable |
| `reference` | English reference answer, for grading; empty if unanswerable |

```python
from datasets import load_dataset

questions = load_dataset("Fla00/fontis-eu-law-qa", split="test")
```

## Suggested metrics

- **Retrieval**: Hit@1, Hit@5 and MRR@10 of the gold articles, counting a match in any language.
- **Answers**: share of answers citing a gold article; correctness against the reference, graded
  by a judge model different from the answering one (correct / partial / incorrect).
- **Refusals**: share of unanswerable questions refused.

## Baseline: Fontis

[Fontis](https://github.com/CraftaleAgency/fontis) with `bge-m3` embeddings, hybrid retrieval in
pgvector and `qwen3:14b` answering, on a single RTX 3060; judged by `gemma4:12b`.

| Metric | Result |
|---|---:|
| Gold article ranked first (Hit@1) | 78% |
| Gold article in the top 5 (Hit@5) | 89% |
| Answers citing a gold article | 88% |
| Answers judged correct / correct or partial | 64% / 96% |
| Unanswerable questions refused | 30 / 30 |

Most partial answers are incomplete lists; the remaining errors are comparisons the model got wrong
despite citing the right articles. Full report, per-language results and findings:
[docs/benchmark](https://github.com/CraftaleAgency/fontis/tree/main/docs/benchmark).

## How it was made

The questions were written by the Fontis author from the text of the regulations. Before writing
each reference answer, its key facts (amounts, deadlines, obligations, dates) were checked against
the gold article, and a script verifies that every gold article exists in the corpus. 18 of the 30
base questions were used to tune Fontis' retrieval (v1); the other 12 were added afterwards and
never used for tuning.

## Limitations

- **Small**: 24 answerable questions per language; differences of a few points are noise.
- **Single author**: the questions reflect what one person thought users would ask.
- **Strict gold**: only the article that states the rule counts; a recital explaining it does not.
- **Point in time**: references follow the texts as published; later amendments are not reflected.
- **Not legal advice**: reference answers summarise the law for evaluation purposes only.

## License

The questions and reference answers are released under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The legislation they refer to is
© European Union, reusable under Commission Decision 2011/833/EU; source: EUR-Lex.

## Citation

```bibtex
@misc{biondo2026fontis,
  author = {Biondo, Flavio},
  title  = {Fontis EU Law QA: a multilingual benchmark for cited answers on EU legislation},
  year   = {2026},
  url    = {https://huggingface.co/datasets/Fla00/fontis-eu-law-qa}
}
```
