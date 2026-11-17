# Data sources

## Demo corpus: EU legislation

| Document | CELEX | Articles | Recitals | Annexes |
|---|---|---:|---:|---:|
| AI Act — Regulation (EU) 2024/1689 | `32024R1689` | 113 | 180 | 13 |
| GDPR — Regulation (EU) 2016/679 | `32016R0679` | 99 | 173 | 0 |
| Data Act — Regulation (EU) 2023/2854 | `32023R2854` | 50 | 119 | 0 |
| NIS2 — Directive (EU) 2022/2555 | `32022L2555` | 46 | 144 | 3 |

Languages: English, Italian, German, French, Spanish — 940 units per language, 4,700 in total.

Added after v0.1.0 (unit counts are printed by `corpus build`):

| Document | CELEX |
|---|---|
| DORA — Regulation (EU) 2022/2554 | `32022R2554` |
| Cyber Resilience Act — Regulation (EU) 2024/2847 | `32024R2847` |
| Digital Services Act — Regulation (EU) 2022/2065 | `32022R2065` |

**eIDAS 2 is not included.** Regulation (EU) 2024/1183 is an amending act: its articles say how to change
Regulation (EU) No 910/2014 ("Article 3 is amended as follows"), so they cannot answer a question on their own.
It needs the consolidated text of Regulation 910/2014, which CELLAR publishes with a different structure; the parser
does not handle it yet.

### What each unit and document carries

`corpus build` adds, besides the text:

| Field | Where | Meaning |
|---|---|---|
| `references` | unit | Other units of the same act that the text refers to ("Articles 6 to 8", "Annex III"), found with rules in the English text and copied to every language. A reference followed by another act ("of Regulation (EU) 2016/679", "TFEU") is left out |
| `applies_from` | unit | Date from which the unit applies when it differs from the document's, e.g. AI Act Article 5 from 2 February 2025; `null` otherwise |
| `applicability_note` | unit | Staggered application or later amendments, in English, e.g. the AI Act high-risk rules postponed by the Digital Omnibus |
| `entry_into_force`, `applies_from` | document | Dates from the act's final provisions |
| `text_version` | document | Publication date in the Official Journal of the text used |
| `definitions_unit` | document | The article with the definitions (`art_3` in the AI Act, `art_4` in the GDPR) |
| `note` | document | How the act applies, e.g. a directive through national transposition |

The dates are curated by hand in `bench/src/fontis_bench/applicability.py`, with the article they come from.
**Texts are not consolidated**: they are as published, and later amendments are only described in the notes. The AI
Act was amended by Regulation (EU) 2026/1744 (Digital Omnibus on AI); the dates of that amendment in the notes come
from secondary sources and must be checked against the Official Journal.

### Hugging Face

`corpus export` writes the built corpus as a Hugging Face dataset folder (`units/<lang>.jsonl`,
`documents.jsonl`); the dataset card and the upload command are in
[`bench/huggingface/corpus`](../bench/huggingface/corpus/README.md).

### How it is downloaded

The EUR-Lex website rejects automated requests, so the corpus comes from **CELLAR**, the
Publications Office repository behind EUR-Lex, through HTTP content negotiation:

```
GET http://publications.europa.eu/resource/celex/32024R1689
Accept: application/xhtml+xml
Accept-Language: ita
```

`fontis-bench corpus fetch` waits one second between requests and caches files in `data/raw/`.

### Why it is useful for evaluation

Each citable unit has the same id in every language (`art_5`, `rct_42`, `anx_III`).
`fontis-bench corpus check` verifies this alignment. It lets the benchmark ask a question in
one language and check whether the answer cites the right unit in a document written in another.

### Licence and attribution

- Reuse is governed by [Commission Decision 2011/833/EU](https://eur-lex.europa.eu/eli/dec/2011/833/oj/eng),
  which allows reuse for commercial and non-commercial purposes with acknowledgement of the source.
- The raw and processed corpus is **not committed** to this repository: it is downloaded at build time.
- Attribution is in [`NOTICE`](../NOTICE). Only the Official Journal of the European Union is authentic.

> Before any commercial use, confirm the current terms on the
> [EUR-Lex reuse page](https://eur-lex.europa.eu/content/help/data-reuse/reuse-contents-eurlex-details.html).
