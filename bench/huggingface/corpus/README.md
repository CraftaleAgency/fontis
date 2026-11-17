---
license: other
license_name: eu-commission-decision-2011-833
license_link: https://eur-lex.europa.eu/eli/dec/2011/833/oj/eng
language:
  - en
  - it
  - de
  - fr
  - es
pretty_name: Fontis EU Digital Law Corpus
size_categories:
  - 10K<n<100K
task_categories:
  - text-retrieval
  - question-answering
tags:
  - legal
  - eu-law
  - multilingual
  - rag
  - ai-act
  - gdpr
  - nis2
  - dora
configs:
  - config_name: all
    data_files: units/*.jsonl
    default: true
  - config_name: en
    data_files: units/eng.jsonl
  - config_name: it
    data_files: units/ita.jsonl
  - config_name: de
    data_files: units/deu.jsonl
  - config_name: fr
    data_files: units/fra.jsonl
  - config_name: es
    data_files: units/spa.jsonl
  - config_name: documents
    data_files: documents.jsonl
---

# Fontis EU Digital Law Corpus

EU digital legislation split into **citable units** (articles, recitals and annexes) in **five languages**,
**aligned across languages**: a unit has the same id in every language (`art_5`, `rct_42`, `anx_III`), so an
article found in French can be cited in Italian, and a question in one language can be evaluated against a text
in another.

| CELEX | Act |
|---|---|
| `32024R1689` | Artificial Intelligence Act — Regulation (EU) 2024/1689 |
| `32016R0679` | General Data Protection Regulation — Regulation (EU) 2016/679 |
| `32023R2854` | Data Act — Regulation (EU) 2023/2854 |
| `32022L2555` | NIS2 Directive — Directive (EU) 2022/2555 |
| `32022R2554` | Digital Operational Resilience Act (DORA) — Regulation (EU) 2022/2554 |
| `32024R2847` | Cyber Resilience Act — Regulation (EU) 2024/2847 |
| `32022R2065` | Digital Services Act — Regulation (EU) 2022/2065 |

Languages: English, Italian, German, French, Spanish. It was built for
[Fontis](https://github.com/CraftaleAgency/fontis), an open-source assistant that answers questions on EU law with
citations, and is the corpus of the [Fontis EU Law QA](https://huggingface.co/datasets/Fla00/fontis-eu-law-qa)
benchmark.

## Fields

`units/<lang>.jsonl`, one unit per line:

| Field | Description |
|---|---|
| `celex` | CELEX number of the act |
| `lang` | ISO 639-2: `eng`, `ita`, `deu`, `fra`, `spa` |
| `unit_id` | `art_<n>`, `rct_<n>` or `anx_<roman>`; the same in every language |
| `unit_type` | `article`, `recital` or `annex` |
| `label` | As printed, in `lang`: "Articolo 5", "ANNEX III", "(42)" |
| `heading` | Title of the article or annex, if any |
| `body` | Text, one paragraph or point per line; list markers ("1.", "(a)") kept at the start of the line |
| `url` | EUR-Lex link to the unit in `lang` |
| `references` | Other units of the same act that the text refers to ("Article 6(2)" → `art_6`), found with rules in the English text |
| `applies_from` | Date from which the unit applies, when it differs from the act's (e.g. AI Act Article 5: 2025-02-02); `null` otherwise |
| `applicability_note` | How the unit applies, when there is more to say (staggered dates, later amendments) |

`documents.jsonl`, one act per line: `celex`, `short_name`, `title`, `entry_into_force`, `applies_from`,
`text_version` (publication date of the text used), `definitions_unit` (the article with the definitions) and
`note`.

```python
from datasets import load_dataset

italian = load_dataset("Fla00/fontis-eu-law-corpus", "it", split="train")
```

## Texts are not consolidated

The texts are those **published in the Official Journal**, downloaded from the Publications Office (CELLAR).
Later amendments are **not** applied. In particular, the AI Act was amended by Regulation (EU) 2026/1744 (Digital
Omnibus on AI), which postpones the high-risk obligations; the AI Act units here are the original 2024 text, and
the affected units carry an `applicability_note`. Application dates come from the final provisions of each act
and were curated by hand; check them against EUR-Lex before relying on them.

## How it was made

`fontis-bench corpus fetch`, `build` and `export` in the [Fontis repository](https://github.com/CraftaleAgency/fontis/tree/main/bench):
the XHTML of each act is split at the `div` elements that EUR-Lex marks with unit ids, list markers are merged with
their text, and `corpus check` verifies that every language has exactly the same unit ids.

## Limitations

- Cross-references are found with regular expressions on the English text: references to another act are
  excluded when the act is named right after them, which misses some indirect wordings.
- Only articles, recitals and annexes are units; footnotes, tables inside annexes and the preamble citations are
  flattened or left out.
- Not legal advice. Only the Official Journal of the European Union is authentic.

## License

© European Union, https://eur-lex.europa.eu. Reuse is authorised under
[Commission Decision 2011/833/EU](https://eur-lex.europa.eu/eli/dec/2011/833/oj/eng), with acknowledgement of the
source. The splitting, ids, references and dates added by Fontis are released under CC BY 4.0.

## Upload

```bash
cd bench
uv run fontis-bench corpus export --out ../data/hf-corpus
cp huggingface/corpus/README.md ../data/hf-corpus/
# HF_TOKEN from the environment, e.g. the repository's .env (not committed)
hf upload Fla00/fontis-eu-law-corpus ../data/hf-corpus . --repo-type dataset
```
