"""EU legislation corpus: download from the Publications Office CELLAR and split into citable units.

Every unit (article, recital, annex) keeps the same id in all languages (e.g. ``art_5``),
which is what makes cross-lingual evaluation possible.
"""

from __future__ import annotations

import json
import re
import time
from dataclasses import asdict, dataclass, field, replace
from pathlib import Path

import httpx
from lxml import html

from . import applicability, references

CELLAR_URL = "http://publications.europa.eu/resource/celex/{celex}"
EURLEX_URL = "https://eur-lex.europa.eu/legal-content/{lang}/TXT/?uri=CELEX:{celex}#{unit_id}"
USER_AGENT = "fontis-bench/0.1 (+https://github.com/Fbiondo00/fontis)"

# ISO 639-2 code used by CELLAR -> ISO 639-1 code used by EUR-Lex URLs.
LANGUAGES = {"eng": "EN", "ita": "IT", "deu": "DE", "fra": "FR", "spa": "ES"}


@dataclass(frozen=True)
class Document:
    celex: str
    short_name: str
    title: str


DOCUMENTS = [
    Document("32024R1689", "AI Act", "Regulation (EU) 2024/1689 on artificial intelligence"),
    Document("32016R0679", "GDPR", "Regulation (EU) 2016/679 on the protection of personal data"),
    Document("32023R2854", "Data Act", "Regulation (EU) 2023/2854 on fair access to and use of data"),
    Document("32022L2555", "NIS2", "Directive (EU) 2022/2555 on a high common level of cybersecurity"),
    Document(
        "32022R2554", "DORA", "Regulation (EU) 2022/2554 on digital operational resilience for the financial sector"
    ),
    Document("32024R2847", "Cyber Resilience Act", "Regulation (EU) 2024/2847 on cybersecurity requirements for "
             "products with digital elements"),
    Document("32022R2065", "Digital Services Act", "Regulation (EU) 2022/2065 on a Single Market For Digital Services"),
]


@dataclass(frozen=True)
class Unit:
    celex: str
    lang: str
    unit_id: str
    unit_type: str  # article | recital | annex
    label: str
    heading: str | None
    body: str
    url: str
    # Added by build(): units of the same act this unit refers to, and when it applies (None: as the document).
    references: list[str] = field(default_factory=list)
    applies_from: str | None = None
    applicability_note: str | None = None


# ---------- Download ----------


def raw_path(data_dir: Path, celex: str, lang: str) -> Path:
    return data_dir / "raw" / f"{celex}.{lang}.xhtml"


def fetch(data_dir: Path, documents=DOCUMENTS, languages=LANGUAGES, force: bool = False) -> list[Path]:
    """Download each document in each language as XHTML via CELLAR content negotiation."""
    paths = []
    with httpx.Client(follow_redirects=True, timeout=120, headers={"User-Agent": USER_AGENT}) as client:
        for doc in documents:
            for lang in languages:
                path = raw_path(data_dir, doc.celex, lang)
                paths.append(path)
                if path.exists() and not force:
                    continue
                response = client.get(
                    CELLAR_URL.format(celex=doc.celex),
                    headers={"Accept": "application/xhtml+xml", "Accept-Language": lang},
                )
                response.raise_for_status()
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(response.content)
                time.sleep(1)  # be polite with the Publications Office
    return paths


# ---------- Parsing ----------

UNIT_ID = re.compile(r"^(art_\d+[a-z]?|rct_\d+|anx_[A-Z0-9]+)$")
# A list marker in its own table cell: "(a)", "1.", "2)", "iv)", "—".
MARKER = re.compile(r"^(\(?[0-9a-z]{1,4}[.)]?|[—–-])$", re.IGNORECASE)
SPACES = re.compile(r"\s+")


def _text(el) -> str:
    return SPACES.sub(" ", "".join(el.itertext())).strip()


def _lines(container, skip=()) -> list[str]:
    """Paragraph texts in document order, with list markers merged into the following text."""
    lines: list[str] = []
    pending = ""
    for p in container.iter("p"):
        if p in skip:
            continue
        text = _text(p)
        if not text:
            continue
        if MARKER.match(text):
            pending = f"{pending} {text}".strip()
            continue
        lines.append(f"{pending} {text}".strip() if pending else text)
        pending = ""
    if pending:
        lines.append(pending)
    return lines


def parse(content: bytes, celex: str, lang: str) -> list[Unit]:
    root = html.fromstring(content)
    lang2 = LANGUAGES[lang]
    units = []
    for el in root.iter("div"):
        unit_id = el.get("id", "")
        if not UNIT_ID.match(unit_id):
            continue
        url = EURLEX_URL.format(lang=lang2, celex=celex, unit_id=unit_id)

        if unit_id.startswith("art_"):
            label_el = next(iter(el.find_class("oj-ti-art")), None)
            heading_el = next(iter(el.find_class("oj-sti-art")), None)
            label = _text(label_el) if label_el is not None else unit_id
            heading = _text(heading_el) if heading_el is not None else None
            body = _lines(el, skip={label_el, heading_el})
            unit_type = "article"
        elif unit_id.startswith("rct_"):
            lines = _lines(el)
            label = f"({unit_id.removeprefix('rct_')})"
            # The recital number is the first marker, already merged into the text.
            body = [re.sub(r"^\(\d+\)\s*", "", lines[0])] + lines[1:] if lines else []
            heading = None
            unit_type = "recital"
        else:
            titles = el.find_class("oj-doc-ti")
            label = _text(titles[0]) if titles else unit_id
            heading = _text(titles[1]) if len(titles) > 1 else None
            body = _lines(el, skip=set(titles[:2]))
            unit_type = "annex"

        if body:
            units.append(Unit(celex, lang, unit_id, unit_type, label, heading, "\n".join(body), url))
    return units


# ---------- Build ----------


def build(data_dir: Path, documents=DOCUMENTS, languages=LANGUAGES) -> dict[str, int]:
    """Parse the raw XHTML into data/corpus/<celex>.<lang>.jsonl plus documents.json.

    Units get their cross-references (found in the English text, or the first language if English is missing)
    and their application dates from :mod:`applicability`; documents get their dates and definitions article.
    """
    out_dir = data_dir / "corpus"
    out_dir.mkdir(parents=True, exist_ok=True)
    counts = {}
    for doc in documents:
        parsed = {lang: parse(raw_path(data_dir, doc.celex, lang).read_bytes(), doc.celex, lang) for lang in languages}
        refs = unit_references(parsed["eng"] if "eng" in parsed else next(iter(parsed.values())))
        for lang, units in parsed.items():
            with (out_dir / f"{doc.celex}.{lang}.jsonl").open("w", encoding="utf-8") as f:
                for unit in units:
                    applies_from, note = applicability.unit_fields(doc.celex, unit.unit_id)
                    unit = replace(unit, references=refs.get(unit.unit_id, []), applies_from=applies_from,
                                   applicability_note=note)
                    f.write(json.dumps(asdict(unit), ensure_ascii=False) + "\n")
            counts[f"{doc.celex}.{lang}"] = len(units)
    (out_dir / "documents.json").write_text(
        json.dumps([document_record(d) for d in documents], ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return counts


def document_record(doc: Document) -> dict:
    """A document as written to documents.json: celex, short_name, title and its applicability fields."""
    return asdict(doc) | applicability.document_fields(doc.celex)


def unit_references(units: list[Unit]) -> dict[str, list[str]]:
    """unit id -> ids of the other units of the same act that its text refers to."""
    existing = {u.unit_id for u in units}
    return {u.unit_id: references.extract(u.body, u.unit_id, existing) for u in units}


def alignment(data_dir: Path, documents=DOCUMENTS, languages=LANGUAGES) -> list[dict]:
    """For each document, check that every language has exactly the same unit ids."""
    report = []
    for doc in documents:
        ids = {}
        for lang in languages:
            path = data_dir / "corpus" / f"{doc.celex}.{lang}.jsonl"
            ids[lang] = {json.loads(line)["unit_id"] for line in path.open(encoding="utf-8")}
        reference = set.union(*ids.values())
        report.append(
            {
                "document": doc.short_name,
                "units": len(reference),
                "missing": {lang: sorted(reference - got)[:5] for lang, got in ids.items() if reference - got},
            }
        )
    return report


# ---------- Export (Hugging Face) ----------

EXPORT_FIELDS = (
    "celex", "lang", "unit_id", "unit_type", "label", "heading", "body", "url", "references", "applies_from",
    "applicability_note",
)


def export(data_dir: Path, out_dir: Path, languages=LANGUAGES) -> dict[str, int]:
    """Write the built corpus as a Hugging Face dataset folder: units/<lang>.jsonl and documents.jsonl.

    The dataset card is bench/huggingface/corpus/README.md; copy it to the folder before uploading.
    """
    corpus_dir = data_dir / "corpus"
    (out_dir / "units").mkdir(parents=True, exist_ok=True)
    counts = {}
    for lang in languages:
        n = 0
        with (out_dir / "units" / f"{lang}.jsonl").open("w", encoding="utf-8") as out:
            for path in sorted(corpus_dir.glob(f"*.{lang}.jsonl")):
                for line in path.open(encoding="utf-8"):
                    unit = json.loads(line)
                    out.write(json.dumps({k: unit.get(k) for k in EXPORT_FIELDS}, ensure_ascii=False) + "\n")
                    n += 1
        counts[lang] = n
    docs = json.loads((corpus_dir / "documents.json").read_text(encoding="utf-8"))
    with (out_dir / "documents.jsonl").open("w", encoding="utf-8") as out:
        for doc in docs:
            out.write(json.dumps(doc, ensure_ascii=False) + "\n")
    return counts
