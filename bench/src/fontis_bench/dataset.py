"""Benchmark dataset checks."""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from .corpus import LANGUAGES


def problems(questions: list[dict], corpus_dir: Path | None = None) -> list[str]:
    """Structural problems in the dataset; with a corpus directory, also gold units that do not exist."""
    found = []
    ids = [q["id"] for q in questions]
    if len(ids) != len(set(ids)):
        found.append("duplicate question ids")

    by_base = defaultdict(dict)
    for q in questions:
        by_base[q["base_id"]][q["lang"]] = q
        if q["answerable"] and (not q["gold"] or not q["reference"]):
            found.append(f"{q['id']}: answerable but without gold units or reference")
        if not q["answerable"] and q["gold"]:
            found.append(f"{q['id']}: unanswerable but with gold units")
    for base, variants in by_base.items():
        if set(variants) != set(LANGUAGES):
            found.append(f"{base}: languages {sorted(variants)} instead of {sorted(LANGUAGES)}")
        if len({json.dumps(v["gold"], sort_keys=True) for v in variants.values()}) > 1:
            found.append(f"{base}: gold units differ between languages")

    if corpus_dir is not None:
        existing = set()
        for path in corpus_dir.glob("*.eng.jsonl"):
            for line in path.open(encoding="utf-8"):
                unit = json.loads(line)
                existing.add((unit["celex"], unit["unit_id"]))
        for q in questions:
            for g in q["gold"]:
                if (g["celex"], g["unit_id"]) not in existing:
                    found.append(f"{q['id']}: gold unit {g['celex']} {g['unit_id']} not in corpus")
    return found
