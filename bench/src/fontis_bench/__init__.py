"""Fontis benchmark and data tooling."""

import argparse
from collections import Counter
from pathlib import Path

from . import corpus

# bench/src/fontis_bench/__init__.py -> repository root
DEFAULT_DATA_DIR = Path(__file__).resolve().parents[3] / "data"


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

    args = parser.parse_args()

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
