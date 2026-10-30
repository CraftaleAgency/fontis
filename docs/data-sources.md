# Data sources

## Demo corpus: EU legislation

| Document | CELEX | Articles | Recitals | Annexes |
|---|---|---:|---:|---:|
| AI Act — Regulation (EU) 2024/1689 | `32024R1689` | 113 | 180 | 13 |
| GDPR — Regulation (EU) 2016/679 | `32016R0679` | 99 | 173 | 0 |
| Data Act — Regulation (EU) 2023/2854 | `32023R2854` | 50 | 119 | 0 |
| NIS2 — Directive (EU) 2022/2555 | `32022L2555` | 46 | 144 | 3 |

Languages: English, Italian, German, French, Spanish — 940 units per language, 4,700 in total.

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
