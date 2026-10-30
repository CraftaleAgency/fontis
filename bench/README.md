# fontis-bench

Python tooling for Fontis: EU legislation corpus (CELLAR download, splitting into citable units, cross-lingual alignment check) and the multilingual RAG benchmark.

```bash
uv run fontis-bench corpus fetch   # download to ../data/raw
uv run fontis-bench corpus build   # split into ../data/corpus/*.jsonl
uv run fontis-bench corpus check   # verify unit ids align across languages
uv run pytest
```
