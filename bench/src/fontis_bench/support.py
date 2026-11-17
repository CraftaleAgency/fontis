"""Support metric: is each sentence of an answer supported by the full text of the sources it cites?

The judge is a different model from the one that answers, and it reads the whole cited unit from the corpus, not
the passage Fontis picked, so it checks Fontis' own verification instead of repeating it.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import httpx

# Same citation markers as the backend: [1], [1, 3], [1-3], [1–3].
CITATION = re.compile(r"\[(\d{1,3}(?:\s*[,;–-]\s*\d{1,3})*)\]")
_NUMBER_OR_RANGE = re.compile(r"(\d{1,3})(?:\s*[–-]\s*(\d{1,3}))?")
# A sentence ends at . ! ? (and any citation markers right after it) followed by a space and a character that does
# not continue the sentence: not a lowercase letter, a digit or a citation ("art. 5", "e.g. the", "para. [1]").
_SENTENCE_END = re.compile(r"(?<=[.!?])((?:\s*\[[\d,;\s–-]+\])*)\s+(?=[^\[\da-zà-ÿ])")

MAX_SOURCE_CHARS = 12_000

SUPPORT_PROMPT = """You check whether a sentence from an answer about EU legislation is supported by its cited sources.
The sentence and the sources may be in different languages (English, Italian, German, French or Spanish).

Sources:
{sources}

Sentence: {sentence}

Grade the sentence:
- "yes": every fact in it (obligations, conditions, numbers, amounts, deadlines) is stated in the sources.
- "partly": some facts are stated in the sources, others are not.
- "no": the sources do not state it, or contradict it.

Reply with JSON only: {{"supported": "yes" | "partly" | "no", "reason": "<one sentence>"}}"""


def citation_numbers(marker: str) -> list[int]:
    numbers = []
    for part in _NUMBER_OR_RANGE.finditer(marker):
        start = int(part.group(1))
        end = int(part.group(2)) if part.group(2) else start
        numbers += range(start, min(end, start + 20) + 1)
    return numbers


def split_sentences(answer: str) -> list[dict]:
    """Sentences of an answer, each with the citation numbers in it and its text without the markers.

    Lines (e.g. bullet points) are always separate sentences.
    """
    sentences = []
    for line in answer.splitlines():
        line = line.strip()
        if not line:
            continue
        # Keep the markers that follow a full stop with the sentence they close.
        parts = _SENTENCE_END.split(line)
        pieces = [parts[i] + (parts[i + 1] if i + 1 < len(parts) else "") for i in range(0, len(parts), 2)]
        for piece in pieces:
            numbers: list[int] = []
            for marker in CITATION.finditer(piece):
                numbers += [n for n in citation_numbers(marker.group(1)) if n not in numbers]
            text = re.sub(r"\s+([.,;:!?])", r"\1", CITATION.sub("", piece)).strip()
            if text:
                sentences.append({"text": text, "citations": numbers})
    return sentences


def answer_sentences(answer: dict) -> list[dict]:
    """Sentences with citations and Fontis' own verdict, from the response's verification when present."""
    verification = answer.get("verification") or {}
    if verification.get("sentences"):
        return [
            {"text": s["text"], "citations": list(s.get("citations") or []), "system": s.get("verdict")}
            for s in verification["sentences"]
        ]
    return [s | {"system": None} for s in split_sentences(answer.get("answer") or "")]


class Corpus:
    """Units of the built corpus, read on demand: (celex, lang, unit_id) -> title and text."""

    def __init__(self, corpus_dir: Path):
        self.corpus_dir = corpus_dir
        self._files: dict[tuple[str, str], dict[str, dict]] = {}

    def unit(self, celex: str, lang: str, unit_id: str) -> dict | None:
        key = (celex, lang)
        if key not in self._files:
            path = self.corpus_dir / f"{celex}.{lang}.jsonl"
            units = {}
            if path.exists():
                for line in path.open(encoding="utf-8"):
                    unit = json.loads(line)
                    units[unit["unit_id"]] = unit
            self._files[key] = units
        return self._files[key].get(unit_id)


def sources_text(answer: dict, numbers: list[int], corpus: Corpus) -> str | None:
    """The full text of the cited units, or None if none of them is in the corpus (e.g. uploaded documents)."""
    by_n = {s["n"]: s for s in answer.get("sources") or answer.get("citations") or []}
    blocks = []
    for n in numbers:
        source = by_n.get(n)
        if source is None:
            continue
        unit = corpus.unit(source["celex"], source.get("matchedLang") or "eng", source["unitId"])
        if unit is None:
            continue
        title = " — ".join(x for x in (source.get("document"), unit["label"], unit.get("heading")) if x)
        blocks.append(f"[{n}] {title}\n{unit['body'][:MAX_SOURCE_CHARS]}")
    return "\n\n".join(blocks) if blocks else None


def judge_sentence(client: httpx.Client, model: str, sentence: str, sources: str, request) -> dict:
    response = request(
        client,
        "POST",
        "/api/chat",
        json={
            "model": model,
            "messages": [{"role": "user", "content": SUPPORT_PROMPT.format(sources=sources, sentence=sentence)}],
            "format": "json",
            "stream": False,
            "think": False,
            # Whole articles: Ollama's default context window would cut them.
            "options": {"temperature": 0, "num_ctx": 16384},
        },
    )
    verdict = json.loads(response.json()["message"]["content"])
    supported = str(verdict.get("supported", "")).lower()
    return {"verdict": supported if supported in ("yes", "partly", "no") else "no", "reason": verdict.get("reason")}
