import json
from pathlib import Path

from fontis_bench.dataset import problems
from fontis_bench.metrics import first_hit_rank, hit_at, percentile, recall_at, reciprocal_rank

GOLD = [("32016R0679", "art_17")]
RESULTS = [("32016R0679", "rct_65"), ("32016R0679", "art_17"), ("32016R0679", "rct_66")]


def test_first_hit_rank_and_derived_metrics():
    rank = first_hit_rank(RESULTS, GOLD)
    assert rank == 2
    assert hit_at(1, rank) == 0.0
    assert hit_at(3, rank) == 1.0
    assert reciprocal_rank(rank) == 0.5


def test_miss():
    rank = first_hit_rank(RESULTS, [("32016R0679", "art_33")])
    assert rank is None
    assert hit_at(10, rank) == 0.0
    assert reciprocal_rank(rank) == 0.0


def test_recall_with_several_gold_units():
    gold = [("32016R0679", "art_17"), ("32016R0679", "art_99")]
    assert recall_at(5, RESULTS, gold) == 0.5


def test_percentile():
    assert percentile([10, 20, 30, 40, 50], 50) == 30
    assert percentile([7], 95) == 7


DATASET = Path(__file__).resolve().parents[1] / "datasets" / "v1.jsonl"


def test_dataset_v1_is_well_formed():
    questions = [json.loads(line) for line in DATASET.open(encoding="utf-8")]
    assert problems(questions) == []
    assert len(questions) == 90
    assert sum(q["answerable"] for q in questions) == 75
