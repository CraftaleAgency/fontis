"""Fontis benchmark and data tooling."""

import argparse
from collections import Counter
from pathlib import Path

from . import corpus, dataset, evaluate, report

# bench/src/fontis_bench/__init__.py -> repository root
ROOT = Path(__file__).resolve().parents[3]
DEFAULT_DATA_DIR = ROOT / "data"
DEFAULT_DATASET = ROOT / "bench" / "datasets" / "v2.jsonl"


def main() -> None:
    parser = argparse.ArgumentParser(prog="fontis-bench")
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR)
    sub = parser.add_subparsers(dest="command", required=True)

    corpus_cmd = sub.add_parser("corpus", help="EU legislation corpus")
    corpus_sub = corpus_cmd.add_subparsers(dest="action", required=True)
    fetch_cmd = corpus_sub.add_parser("fetch", help="download documents from CELLAR")
    fetch_cmd.add_argument("--force", action="store_true", help="download again even if cached")
    corpus_sub.add_parser("build", help="split documents into citable units (JSONL)")
    corpus_sub.add_parser("check", help="check that unit ids align across languages")

    eval_cmd = sub.add_parser("eval", help="benchmark a running Fontis backend")
    eval_cmd.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    eval_cmd.add_argument("--run", default="v2", help="run name: results go to <data-dir>/results/<run>")
    eval_sub = eval_cmd.add_subparsers(dest="action", required=True)
    eval_sub.add_parser("check", help="validate the dataset against the corpus")
    for name, help_text in (("retrieval", "query /api/search in every mode"), ("answers", "query /api/ask")):
        phase = eval_sub.add_parser(name, help=help_text)
        phase.add_argument("--api", default="http://localhost:8080")
    tune_cmd = eval_sub.add_parser("tune", help="compare ranking settings on retrieval")
    tune_cmd.add_argument("--api", default="http://localhost:8080")
    judge_cmd = eval_sub.add_parser("judge", help="grade answers with a different model")
    judge_cmd.add_argument("--ollama", default="http://localhost:11434")
    judge_cmd.add_argument("--model", default="gemma4:12b")
    report_cmd = eval_sub.add_parser("report", help="write the summary and the Markdown report")
    report_cmd.add_argument("--out", type=Path, default=ROOT / "docs" / "benchmark")

    args = parser.parse_args()
    if args.command == "corpus":
        _corpus(args)
    else:
        _eval(args)


def _corpus(args) -> None:
    if args.action == "fetch":
        paths = corpus.fetch(args.data_dir, force=args.force)
        print(f"{len(paths)} documents in {args.data_dir / 'raw'}")
    elif args.action == "build":
        counts = corpus.build(args.data_dir)
        for key, n in counts.items():
            print(f"{key:<20} {n:>5} units")
        print(f"{'total':<20} {sum(counts.values()):>5} units")
    elif args.action == "check":
        ok = True
        for row in corpus.alignment(args.data_dir):
            status = "aligned" if not row["missing"] else f"MISSING {row['missing']}"
            ok &= not row["missing"]
            print(f"{row['document']:<10} {row['units']:>5} units  {status}")
        if not ok:
            raise SystemExit(1)
        types = Counter()
        for path in (args.data_dir / "corpus").glob("*.eng.jsonl"):
            for line in path.open(encoding="utf-8"):
                types[line.split('"unit_type": "')[1].split('"')[0]] += 1
        print("per language:", dict(types))


def _eval(args) -> None:
    questions = evaluate.load_dataset(args.dataset)
    run_dir = args.data_dir / "results" / args.run
    if args.action == "check":
        found = dataset.problems(questions, args.data_dir / "corpus")
        for problem in found:
            print(problem)
        answerable = sum(q["answerable"] for q in questions)
        print(f"{len(questions)} questions ({answerable} answerable): {'OK' if not found else 'PROBLEMS'}")
        if found:
            raise SystemExit(1)
    elif args.action == "retrieval":
        print(f"{evaluate.run_retrieval(questions, args.api, run_dir)} searches -> {run_dir / 'retrieval.jsonl'}")
    elif args.action == "answers":
        print(f"{evaluate.run_answers(questions, args.api, run_dir)} answers -> {run_dir / 'answers.jsonl'}")
    elif args.action == "tune":
        count = evaluate.run_tuning(questions, args.api, run_dir)
        print(f"{count} searches -> {run_dir / 'tuning.jsonl'}\n")
        print(report.tuning_table(questions, run_dir))
    elif args.action == "judge":
        count = evaluate.run_judge(questions, args.ollama, args.model, run_dir)
        print(f"{count} verdicts -> {run_dir / 'judge.jsonl'}")
    elif args.action == "report":
        summary = report.write(
            questions,
            run_dir,
            args.out / f"{args.run}-summary.json",
            args.out / f"{args.run}.md",
            title=f"Fontis benchmark — {args.run}",
        )
        print(report.markdown(summary, f"Fontis benchmark — {args.run}"))
