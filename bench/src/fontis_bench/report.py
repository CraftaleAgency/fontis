"""Turn a run directory into a summary (JSON) and a Markdown report."""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from .evaluate import MODES, read_jsonl
from .metrics import JUDGE_SCORES, first_hit_rank, hit_at, mean, percentile, recall_at, reciprocal_rank

LANG_NAMES = {"eng": "English", "ita": "Italian", "deu": "German", "fra": "French", "spa": "Spanish"}
REFUSALS = {"NO_SOURCE", "NO_ANSWER"}


def summarize(questions: list[dict], run_dir: Path) -> dict:
    by_id = {q["id"]: q for q in questions}
    answerable = [q for q in questions if q["answerable"]]

    # ---------- Retrieval ----------
    retrieval = defaultdict(lambda: defaultdict(list))  # mode -> group -> rows
    cross_lingual_hits = []
    for r in read_jsonl(run_dir / "retrieval.jsonl"):
        q = by_id.get(r["id"])
        if q is None or not q["answerable"]:
            continue
        units = [(x["celex"], x["unit_id"]) for x in r["results"]]
        gold = [(g["celex"], g["unit_id"]) for g in q["gold"]]
        rank = first_hit_rank(units, gold)
        row = {
            "hit@1": hit_at(1, rank),
            "hit@3": hit_at(3, rank),
            "hit@5": hit_at(5, rank),
            "mrr@10": reciprocal_rank(rank),
            "recall@5": recall_at(5, units, gold),
            "ms": r["ms"],
        }
        for group in ("all", q["lang"]):
            retrieval[r["mode"]][group].append(row)
        if r["mode"] == "hybrid" and rank is not None and rank <= 5:
            cross_lingual_hits.append(r["results"][rank - 1]["matched_lang"] != q["lang"])

    retrieval_summary = {
        mode: {
            group: {metric: mean(row[metric] for row in rows) for metric in rows[0] if metric != "ms"}
            | {"n": len(rows), "p50_ms": percentile((row["ms"] for row in rows), 50)}
            for group, rows in groups.items()
        }
        for mode, groups in retrieval.items()
    }

    # ---------- Answers ----------
    answers = {a["id"]: a for a in read_jsonl(run_dir / "answers.jsonl")}
    judged = {j["id"]: j for j in read_jsonl(run_dir / "judge.jsonl")}
    answer_rows = defaultdict(list)
    for q in answerable:
        a = answers.get(q["id"])
        if a is None:
            continue
        gold = {(g["celex"], g["unit_id"]) for g in q["gold"]}
        cited = {(c["celex"], c["unitId"]) for c in a["citations"]}
        verdict = judged.get(q["id"], {}).get("verdict")
        row = {
            "answered": 1.0 if a["status"] == "ANSWERED" else 0.0,
            "cites_gold": 1.0 if cited & gold else 0.0,
            # Unanswered questions score 0: a refusal on an answerable question is a miss.
            "correctness": JUDGE_SCORES.get(verdict, 0.0),
            "correct": 1.0 if verdict == "correct" else 0.0,
        }
        for group in ("all", q["lang"]):
            answer_rows[group].append(row)

    refusal_rows = defaultdict(list)
    for q in questions:
        a = answers.get(q["id"])
        if a is None or q["answerable"]:
            continue
        for group in ("all", q["lang"]):
            refusal_rows[group].append(1.0 if a["status"] in REFUSALS else 0.0)

    answered = [a for a in answers.values() if a["status"] == "ANSWERED"]
    answer_summary = {
        group: {metric: mean(row[metric] for row in rows) for metric in rows[0]} | {"n": len(rows)}
        for group, rows in answer_rows.items()
    }
    for group, values in refusal_rows.items():
        answer_summary.setdefault(group, {})["correct_refusal"] = mean(values)

    statuses = defaultdict(int)
    for a in answers.values():
        statuses[a["status"]] += 1

    return {
        "questions": len(questions),
        "answerable": len(answerable),
        "retrieval": retrieval_summary,
        "cross_lingual_share_of_hybrid_hits": mean(cross_lingual_hits),
        "answers": answer_summary,
        "statuses": dict(statuses),
        "latency_ms": {
            "retrieval_p50": percentile((a["retrievalMs"] for a in answers.values()), 50),
            "generation_p50": percentile((a["generationMs"] for a in answered), 50),
            "generation_p95": percentile((a["generationMs"] for a in answered), 95),
        },
        "models": sorted({a["model"] for a in answered if a.get("model")}),
        "judge": sorted({j["judge"] for j in judged.values()}),
    }


def _pct(x: float) -> str:
    return "–" if x != x else f"{x * 100:.0f}%"


def markdown(summary: dict, title: str) -> str:
    langs = [lang for lang in LANG_NAMES if lang in summary["retrieval"].get("hybrid", {})]
    lines = [f"# {title}", ""]
    lines += [
        f"{summary['questions']} questions: {summary['answerable']} answerable "
        f"({summary['answerable'] // 5} per language × 5 languages) and "
        f"{summary['questions'] - summary['answerable']} that the corpus cannot answer.",
        f"Answer model: {', '.join(summary['models']) or '–'} · judge: {', '.join(summary['judge']) or '–'}.",
        "",
        "## Retrieval",
        "",
        "Gold unit (article, recital or annex) found among the results, any language.",
        "",
        "| Mode | Hit@1 | Hit@3 | Hit@5 | MRR@10 | Recall@5 | p50 latency |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for mode in MODES:
        s = summary["retrieval"].get(mode, {}).get("all")
        if s:
            lines.append(
                f"| {mode} | {_pct(s['hit@1'])} | {_pct(s['hit@3'])} | {_pct(s['hit@5'])} | "
                f"{s['mrr@10']:.2f} | {_pct(s['recall@5'])} | {s['p50_ms']:.0f} ms |"
            )
    lines += [
        "",
        f"In hybrid mode, {_pct(summary['cross_lingual_share_of_hybrid_hits'])} of the gold units found in the "
        "top 5 matched a text in **another language** than the question.",
        "",
        "### Hybrid retrieval by question language",
        "",
        "| Language | Hit@1 | Hit@5 | MRR@10 |",
        "|---|---:|---:|---:|",
    ]
    for lang in langs:
        s = summary["retrieval"]["hybrid"][lang]
        lines.append(f"| {LANG_NAMES[lang]} | {_pct(s['hit@1'])} | {_pct(s['hit@5'])} | {s['mrr@10']:.2f} |")

    a = summary["answers"]
    lat = summary["latency_ms"]
    lines += [
        "",
        "## Answers",
        "",
        "| Language | Answered | Cites gold unit | Correct (judge) | Correctness score | Correct refusals |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for lang in ["all", *langs]:
        s = a.get(lang)
        if s:
            name = "**All**" if lang == "all" else LANG_NAMES[lang]
            lines.append(
                f"| {name} | {_pct(s.get('answered', float('nan')))} | {_pct(s.get('cites_gold', float('nan')))} | "
                f"{_pct(s.get('correct', float('nan')))} | {_pct(s.get('correctness', float('nan')))} | "
                f"{_pct(s.get('correct_refusal', float('nan')))} |"
            )
    lines += [
        "",
        "- *Correctness score*: correct = 1, partial = 0.5, incorrect or not answered = 0.",
        "- *Correct refusals*: share of unanswerable questions answered with `NO_SOURCE` or `NO_ANSWER`.",
        f"- Statuses: {', '.join(f'{k} {v}' for k, v in sorted(summary['statuses'].items()))}.",
        f"- Latency: retrieval p50 {lat['retrieval_p50']:.0f} ms; generation p50 {lat['generation_p50']:.0f} ms, "
        f"p95 {lat['generation_p95']:.0f} ms.",
        "",
    ]
    return "\n".join(lines)


def write(questions: list[dict], run_dir: Path, summary_path: Path, report_path: Path, title: str) -> dict:
    summary = summarize(questions, run_dir)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(markdown(summary, title), encoding="utf-8")
    return summary


def tuning_table(questions: list[dict], run_dir: Path) -> str:
    """Markdown table of retrieval metrics per ranking setting, best MRR first."""
    by_id = {q["id"]: q for q in questions}
    rows = defaultdict(list)
    for r in read_jsonl(run_dir / "tuning.jsonl"):
        q = by_id[r["id"]]
        units = [(x["celex"], x["unit_id"]) for x in r["results"]]
        rank = first_hit_rank(units, [(g["celex"], g["unit_id"]) for g in q["gold"]])
        rows[r["config"]].append((hit_at(1, rank), hit_at(5, rank), reciprocal_rank(rank)))
    table = [
        (config, mean(h1 for h1, _, _ in vals), mean(h5 for _, h5, _ in vals), mean(rr for _, _, rr in vals), len(vals))
        for config, vals in rows.items()
    ]
    table.sort(key=lambda row: row[3], reverse=True)
    lines = ["| Setting | Hit@1 | Hit@5 | MRR@10 | n |", "|---|---:|---:|---:|---:|"]
    lines += [f"| `{c}` | {_pct(h1)} | {_pct(h5)} | {rr:.3f} | {n} |" for c, h1, h5, rr, n in table]
    return "\n".join(lines)
