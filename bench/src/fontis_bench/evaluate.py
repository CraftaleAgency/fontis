"""Benchmark runs against a running Fontis backend.

Each phase writes one JSONL file per run directory and skips questions already done, so a run can be
interrupted and resumed:

    retrieval.jsonl  /api/search in hybrid, vector and text modes
    answers.jsonl    /api/ask
    judge.jsonl      answer correctness judged by a different model than the one answering
"""

from __future__ import annotations

import json
import os
import time
from collections.abc import Callable, Iterator
from contextlib import contextmanager
from pathlib import Path

import httpx

MODES = ("hybrid", "vector", "text")
SEARCH_LIMIT = 10

JUDGE_PROMPT = """You are grading an answer about EU legislation against a reference answer.
The question and the answer may be in English, Italian, German, French or Spanish; the reference is in English.

Question: {question}

Reference answer: {reference}

Answer to grade: {answer}

Grade the answer:
- "correct": it states the key facts of the reference (numbers, deadlines, obligations) without contradicting it.
- "partial": it is relevant and not wrong, but misses key facts of the reference.
- "incorrect": it contradicts the reference, is off-topic, or misses the point.

Reply with JSON only: {{"verdict": "correct" | "partial" | "incorrect", "reason": "<one sentence>"}}"""


def load_dataset(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.open(encoding="utf-8") if line.strip()]


def _done_ids(path: Path, key: Callable[[dict], str]) -> set[str]:
    if not path.exists():
        return set()
    return {key(json.loads(line)) for line in path.open(encoding="utf-8") if line.strip()}


def _append(path: Path, record: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")


def _request(client: httpx.Client, method: str, url: str, attempts: int = 6, **kwargs) -> httpx.Response:
    """Send a request, retrying on timeouts, connection errors and server errors (models run on a remote GPU).

    The waits grow to a few minutes, so a model server that stops answering for a while does not end a run.
    """
    for attempt in range(1, attempts + 1):
        try:
            response = client.request(method, url, **kwargs)
            response.raise_for_status()
            return response
        except httpx.HTTPStatusError as e:
            if e.response.status_code < 500 or attempt == attempts:
                raise
        except (httpx.TimeoutException, httpx.NetworkError):
            if attempt == attempts:
                raise
        time.sleep(min(15 * 2 ** (attempt - 1), 240))
    raise AssertionError("unreachable")


@contextmanager
def api_client(api: str, timeout: float) -> Iterator[httpx.Client]:
    """Client for the Fontis API: fetches the CSRF cookie and sends it back as a header on every request.

    FONTIS_INSECURE_TLS=1 accepts a self-signed certificate, e.g. the Docker stack on https://localhost.
    With FONTIS_ADMIN_EMAIL and FONTIS_ADMIN_PASSWORD it signs in: /api/search is for administrators.
    """
    verify = os.environ.get("FONTIS_INSECURE_TLS") != "1"
    with httpx.Client(base_url=api, timeout=timeout, verify=verify) as client:
        client.get("/api/auth/me")
        token = client.cookies.get("XSRF-TOKEN")
        if token:
            client.headers["X-XSRF-TOKEN"] = token
        email, password = os.environ.get("FONTIS_ADMIN_EMAIL"), os.environ.get("FONTIS_ADMIN_PASSWORD")
        if email and password:
            client.post("/api/auth/login", json={"email": email, "password": password}).raise_for_status()
        yield client


def read_jsonl(path: Path) -> Iterator[dict]:
    if path.exists():
        yield from (json.loads(line) for line in path.open(encoding="utf-8") if line.strip())


def run_retrieval(questions: list[dict], api: str, run_dir: Path) -> int:
    out = run_dir / "retrieval.jsonl"
    done = _done_ids(out, lambda r: f"{r['id']}|{r['mode']}")
    count = 0
    with api_client(api, timeout=120) as client:
        for q in questions:
            for mode in MODES:
                if f"{q['id']}|{mode}" in done:
                    continue
                start = time.perf_counter()
                response = _request(
                    client,
                    "GET",
                    "/api/search",
                    params={"q": q["question"], "lang": q["lang"], "limit": SEARCH_LIMIT, "mode": mode.upper()},
                )
                _append(
                    out,
                    {
                        "id": q["id"],
                        "mode": mode,
                        "ms": round((time.perf_counter() - start) * 1000),
                        "results": [
                            {"celex": s["celex"], "unit_id": s["unitId"], "matched_lang": s["matchedLang"]}
                            for s in response.json()
                        ],
                    },
                )
                count += 1
    return count


def run_answers(questions: list[dict], api: str, run_dir: Path) -> int:
    out = run_dir / "answers.jsonl"
    done = _done_ids(out, lambda r: r["id"])
    count = 0
    with api_client(api, timeout=600) as client:
        for q in questions:
            if q["id"] in done:
                continue
            response = _request(client, "POST", "/api/ask", json={"question": q["question"], "lang": q["lang"]})
            _append(out, {"id": q["id"], **response.json()})
            count += 1
    return count


def run_judge(questions: list[dict], ollama: str, model: str, run_dir: Path) -> int:
    """Grade every answer to an answerable question that the system did answer."""
    answers = {a["id"]: a for a in read_jsonl(run_dir / "answers.jsonl")}
    out = run_dir / "judge.jsonl"
    done = _done_ids(out, lambda r: r["id"])
    count = 0
    with httpx.Client(base_url=ollama, timeout=600) as client:
        for q in questions:
            answer = answers.get(q["id"])
            if not q["answerable"] or answer is None or not answer.get("answer") or q["id"] in done:
                continue
            prompt = JUDGE_PROMPT.format(question=q["question"], reference=q["reference"], answer=answer["answer"])
            response = _request(
                client,
                "POST",
                "/api/chat",
                json={
                    "model": model,
                    "messages": [{"role": "user", "content": prompt}],
                    "format": "json",
                    "stream": False,
                    "think": False,
                    "options": {"temperature": 0},
                },
            )
            verdict = json.loads(response.json()["message"]["content"])
            _append(
                out,
                {
                    "id": q["id"],
                    "judge": model,
                    "verdict": str(verdict.get("verdict", "")).lower(),
                    "reason": verdict.get("reason"),
                },
            )
            count += 1
    return count


# Ranking settings tried by `eval tune`: text weight, recital weight, sum scores across languages.
TUNING_GRID = [
    {"mode": "VECTOR"},
    *[
        {"mode": "HYBRID", "textWeight": tw, "recitalWeight": rw, "sumLanguages": sl}
        for tw in (1.0, 0.3, 0.0)
        for rw in (1.0, 0.6)
        for sl in (False, True)
    ],
]


def config_key(config: dict) -> str:
    return ",".join(f"{k}={v}" for k, v in config.items())


def run_tuning(questions: list[dict], api: str, run_dir: Path, grid: list[dict] = TUNING_GRID) -> int:
    """Retrieval for every answerable question under every ranking setting."""
    out = run_dir / "tuning.jsonl"
    done = _done_ids(out, lambda r: f"{r['id']}|{r['config']}")
    count = 0
    with api_client(api, timeout=120) as client:
        for config in grid:
            key = config_key(config)
            for q in questions:
                if not q["answerable"] or f"{q['id']}|{key}" in done:
                    continue
                params = {"q": q["question"], "lang": q["lang"], "limit": SEARCH_LIMIT, **config}
                params = {k: str(v).lower() if isinstance(v, bool) else v for k, v in params.items()}
                response = _request(client, "GET", "/api/search", params=params)
                _append(
                    out,
                    {
                        "id": q["id"],
                        "config": key,
                        "results": [{"celex": s["celex"], "unit_id": s["unitId"]} for s in response.json()],
                    },
                )
                count += 1
    return count
