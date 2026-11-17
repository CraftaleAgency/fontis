"""Experiment: does an NLI (entailment) model tell supported answer sentences from unsupported ones?

Reference: the judge's sentence verdicts from `fontis-bench eval support` (support.jsonl). For each sentence,
every paragraph of the units it cites (in the answer's language) is a premise; the sentence is the hypothesis;
the sentence's score is the highest entailment probability over its premises. Compared with the embedding
similarity of Fontis' own check (the response's verification).

    uv run --with torch --with transformers --with sentencepiece python experiments/nli_support.py \
        --run v2.1-next --model MoritzLaurer/multilingual-MiniLMv2-L6-mnli-xnli

Runs on CPU; writes <run>/nli-<model>.jsonl and prints the comparison.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from pathlib import Path

import httpx
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

ROOT = Path(__file__).resolve().parents[2]
MARKERS = re.compile(r"\s*\[[\d\s,;–-]{1,20}]")


def load_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.open(encoding="utf-8") if line.strip()]


def unit_lines(corpus: Path, celex: str, unit_id: str, lang: str, cache: dict) -> list[str]:
    key = (celex, lang)
    if key not in cache:
        path = corpus / f"{celex}.{lang}.jsonl"
        cache[key] = {u["unit_id"]: u for u in load_jsonl(path)} if path.exists() else {}
    unit = cache[key].get(unit_id)
    if unit is None:
        return []
    lines = [line.strip() for line in unit["body"].split("\n") if line.strip()]
    # A list point needs its lead-in ("The following practices shall be prohibited:") to be read alone, and a
    # sentence may combine two consecutive points: single lines, lead-in + line, and pairs of lines.
    lead = lines[0] if lines else ""
    premises = []
    for i, line in enumerate(lines):
        premises.append(line[:1500])
        if i > 0 and len(line) < 600 and lead.endswith(":"):
            premises.append(f"{lead} {line}"[:1500])
        if i + 1 < len(lines):
            premises.append(f"{line} {lines[i + 1]}"[:1500])
    return premises


def embed(client: httpx.Client, texts: list[str], cache: dict) -> list[list[float]]:
    missing = [t for t in dict.fromkeys(texts) if t not in cache]
    for i in range(0, len(missing), 64):
        batch = missing[i : i + 64]
        vectors = client.post("/api/embed", json={"model": "bge-m3", "input": batch}).raise_for_status().json()
        cache.update(zip(batch, vectors["embeddings"], strict=True))
    return [cache[t] for t in texts]


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=True))
    return dot / ((sum(x * x for x in a) ** 0.5) * (sum(y * y for y in b) ** 0.5) or 1.0)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", default="v2.1-next")
    parser.add_argument("--model", default="MoritzLaurer/multilingual-MiniLMv2-L6-mnli-xnli")
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--top", type=int, default=4, help="premises per sentence, preselected with bge-m3 (0 = all)")
    parser.add_argument("--ollama", default=os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434"))
    args = parser.parse_args()
    ollama = httpx.Client(base_url=args.ollama, timeout=300)
    vectors: dict = {}

    run_dir = ROOT / "data" / "results" / args.run
    corpus = ROOT / "data" / "corpus"
    answers = {a["id"]: a for a in load_jsonl(run_dir / "answers.jsonl")}
    support = {r["id"]: r for r in load_jsonl(run_dir / "support.jsonl")}

    tokenizer = AutoTokenizer.from_pretrained(args.model)
    model = AutoModelForSequenceClassification.from_pretrained(args.model).eval()
    labels = {v.lower(): k for k, v in model.config.id2label.items()}
    entail, contra = labels["entailment"], labels["contradiction"]
    torch.set_num_threads(max(1, torch.get_num_threads()))

    out_path = run_dir / f"nli-{args.model.split('/')[-1]}-top{args.top}.jsonl"
    cache: dict = {}
    rows = []
    start = time.perf_counter()
    pairs_total = 0
    for qid, row in support.items():
        answer = answers[qid]
        lang = qid.rsplit(".", 1)[1]
        by_n = {s["n"]: s for s in answer["sources"]}
        similarity = {s["text"]: s.get("similarity") for s in (answer.get("verification") or {}).get("sentences", [])}
        for sentence in row["sentences"]:
            if sentence["verdict"] not in ("yes", "partly", "no") or not sentence.get("citations"):
                continue
            hypothesis = MARKERS.sub("", sentence["text"]).strip()
            premises = []
            for n in sentence["citations"]:
                source = by_n.get(n)
                if source:
                    premises += unit_lines(corpus, source["celex"], source["unitId"], lang, cache)
            if not premises:
                continue
            if args.top and len(premises) > args.top:
                h, *p = embed(ollama, [hypothesis, *premises], vectors)
                ranked = sorted(range(len(premises)), key=lambda k: cosine(h, p[k]), reverse=True)
                premises = [premises[k] for k in ranked[: args.top]]
            best_e, best_c, best_premise = 0.0, 0.0, ""
            for i in range(0, len(premises), args.batch):
                batch = premises[i : i + args.batch]
                enc = tokenizer(batch, [hypothesis] * len(batch), truncation="only_first", max_length=512,
                                padding=True, return_tensors="pt")
                with torch.no_grad():
                    probs = torch.softmax(model(**enc).logits, dim=-1)
                for j, p in enumerate(probs):
                    if p[entail].item() > best_e:
                        best_e, best_c, best_premise = p[entail].item(), p[contra].item(), batch[j]
            pairs_total += len(premises)
            rows.append({"id": qid, "text": sentence["text"], "judge": sentence["verdict"],
                         "system": sentence.get("system"), "similarity": similarity.get(sentence["text"]),
                         "entailment": round(best_e, 4), "contradiction": round(best_c, 4),
                         "premise": best_premise[:300]})
        print(f"{len(rows)} sentences, {pairs_total} pairs, {time.perf_counter() - start:.0f} s", flush=True)

    with out_path.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    report(rows)


def auc(scores_pos: list[float], scores_neg: list[float]) -> float:
    """Probability that a supported sentence scores above an unsupported one."""
    wins = sum((p > n) + 0.5 * (p == n) for p in scores_pos for n in scores_neg)
    return wins / (len(scores_pos) * len(scores_neg))


def report(rows: list[dict]) -> None:
    yes = [r for r in rows if r["judge"] == "yes"]
    other = [r for r in rows if r["judge"] != "yes"]
    print(f"\n{len(rows)} sentences: judge yes {len(yes)}, partly/no {len(other)}")
    print(f"AUC entailment: {auc([r['entailment'] for r in yes], [r['entailment'] for r in other]):.3f}")
    sims = [r for r in rows if r["similarity"] is not None]
    print(f"AUC similarity: {auc([r['similarity'] for r in sims if r['judge'] == 'yes'], [r['similarity'] for r in sims if r['judge'] != 'yes']):.3f}")
    print("\nthreshold  flagged  caught(not-yes)  false-alarms(yes)")
    for t in (0.1, 0.2, 0.3, 0.5, 0.7, 0.9):
        flagged = [r for r in rows if r["entailment"] < t]
        caught = sum(1 for r in flagged if r["judge"] != "yes")
        print(f"  {t:<9}{len(flagged):>7}{caught:>10}/{len(other):<6}{len(flagged) - caught:>10}")


if __name__ == "__main__":
    main()
