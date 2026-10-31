"""Retrieval and answer metrics. Units are identified by (celex, unit_id), whatever the language."""

from __future__ import annotations

import statistics
from collections.abc import Iterable, Sequence

Unit = tuple[str, str]


def first_hit_rank(results: Sequence[Unit], gold: Iterable[Unit]) -> int | None:
    """1-based rank of the first gold unit in the results, or None if no gold unit was retrieved."""
    gold = set(gold)
    for rank, unit in enumerate(results, start=1):
        if unit in gold:
            return rank
    return None


def hit_at(k: int, rank: int | None) -> float:
    return 1.0 if rank is not None and rank <= k else 0.0


def reciprocal_rank(rank: int | None) -> float:
    return 0.0 if rank is None else 1.0 / rank


def recall_at(k: int, results: Sequence[Unit], gold: Iterable[Unit]) -> float:
    gold = set(gold)
    return len(gold & set(results[:k])) / len(gold) if gold else 0.0


def mean(values: Iterable[float]) -> float:
    values = list(values)
    return sum(values) / len(values) if values else float("nan")


def percentile(values: Iterable[float], p: float) -> float:
    values = sorted(values)
    if not values:
        return float("nan")
    if len(values) == 1:
        return values[0]
    return statistics.quantiles(values, n=100, method="inclusive")[round(p) - 1]


JUDGE_SCORES = {"correct": 1.0, "partial": 0.5, "incorrect": 0.0}
