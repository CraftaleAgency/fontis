"""When each act, and each part of it, applies: curated from the final provisions of each act.

Dates are those of the texts as published in the Official Journal. Later amendments are not applied
to the dates; where one is known, it is described in a note so that answers can warn about it.

``applies_from`` on a unit is ``None`` when the unit applies from the document's ``applies_from``.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Applicability:
    entry_into_force: str
    applies_from: str
    text_version: str  # publication date of the Official Journal text used
    definitions_unit: str  # the article with the definitions
    note: str | None = None
    # unit id -> date from which that unit applies, when it differs from the document
    unit_dates: dict[str, str] = field(default_factory=dict)
    # unit id -> note on how the unit applies
    unit_notes: dict[str, str] = field(default_factory=dict)


def _articles(first: int, last: int) -> list[str]:
    return [f"art_{n}" for n in range(first, last + 1)]


# ---------- AI Act, Regulation (EU) 2024/1689: Article 113 ----------

# The Digital Omnibus dates below come from secondary sources (law-firm notes, November 2026).
# TODO: check them against Regulation (EU) 2026/1744 in the Official Journal before relying on them.
_AI_OMNIBUS = (
    "Amended by Regulation (EU) 2026/1744 (Digital Omnibus on AI, in force 27 July 2026): obligations for "
    "Annex III high-risk systems apply from 2 December 2027 and for Annex I systems from 2 August 2028. "
    "The text in this corpus is the original 2024 text."
)
_AI_HIGH_RISK = (
    "High-risk AI rules: the dates of the original text were postponed by Regulation (EU) 2026/1744 "
    "(Digital Omnibus on AI): Annex III systems from 2 December 2027, Annex I systems from 2 August 2028."
)

_ai_dates: dict[str, str] = {}
# Article 113(a): Chapters I and II from 2 February 2025.
_ai_dates |= dict.fromkeys(_articles(1, 5), "2025-02-02")
# Article 113(b): Chapter III Section 4, Chapter V, Chapter VII, Chapter XII and Article 78 from
# 2 August 2025, with the exception of Article 101.
for _unit in [*_articles(28, 39), *_articles(51, 56), *_articles(64, 70), *_articles(99, 100), "art_78"]:
    _ai_dates[_unit] = "2025-08-02"

_ai_notes: dict[str, str] = dict.fromkeys([*_articles(6, 49), "anx_I", "anx_III"], _AI_HIGH_RISK)
# Article 113(c): Article 6(1) and the corresponding obligations from 2 August 2027.
_ai_notes["art_6"] = (
    "Article 6(1) (AI systems that are safety components of products under Annex I) applies from 2 August 2027 "
    "in the original text; the rest of the article from 2 August 2026. " + _AI_HIGH_RISK
)
_ai_notes["art_101"] = (
    "Unlike the rest of Chapter XII, Article 101 (fines for GPAI providers) applies from 2 August 2026."
)

AI_ACT = Applicability(
    entry_into_force="2024-08-01",
    applies_from="2026-08-02",
    text_version="2024-07-12",
    definitions_unit="art_3",
    note=_AI_OMNIBUS,
    unit_dates=_ai_dates,
    unit_notes=_ai_notes,
)

# ---------- GDPR, Regulation (EU) 2016/679: Article 99 ----------

GDPR = Applicability(
    entry_into_force="2016-05-24",
    applies_from="2018-05-25",
    text_version="2016-05-04",
    definitions_unit="art_4",
)

# ---------- Data Act, Regulation (EU) 2023/2854: Article 50 ----------

DATA_ACT = Applicability(
    entry_into_force="2024-01-11",
    applies_from="2025-09-12",
    text_version="2023-12-22",
    definitions_unit="art_2",
    unit_notes={
        "art_3": "The obligation of Article 3(1) applies to connected products and related services placed on the "
        "market after 12 September 2026.",
        "art_13": "Chapter IV applies to contracts concluded after 12 September 2025, and from 12 September 2027 to "
        "contracts concluded on or before that date if they are of indefinite duration or expire at least ten years "
        "after 11 January 2024.",
    },
)

# ---------- NIS2, Directive (EU) 2022/2555: Articles 41 and 45 ----------

NIS2 = Applicability(
    entry_into_force="2023-01-16",
    applies_from="2024-10-18",
    text_version="2022-12-27",
    definitions_unit="art_6",
    note="A directive: it applies to organisations through the national laws that transpose it. Member States had to "
    "adopt them by 17 October 2024 and apply them from 18 October 2024.",
)

# ---------- DORA, Regulation (EU) 2022/2554: Article 64 ----------

DORA = Applicability(
    entry_into_force="2023-01-16",
    applies_from="2025-01-17",
    text_version="2022-12-27",
    definitions_unit="art_3",
)

# ---------- Cyber Resilience Act, Regulation (EU) 2024/2847: Article 71 ----------

# TODO: check that Chapter IV (notification of conformity assessment bodies) is Articles 35 to 51.
CRA = Applicability(
    entry_into_force="2024-12-10",
    applies_from="2027-12-11",
    text_version="2024-11-20",
    definitions_unit="art_3",
    unit_dates={"art_14": "2026-09-11", **dict.fromkeys(_articles(35, 51), "2026-06-11")},
)

# ---------- Digital Services Act, Regulation (EU) 2022/2065: Article 93 ----------

# Article 93(2): Article 24(2), (3) and (6), Article 33(3) to (6), Article 37(7), Article 40(13), Article 43
# and Sections 4, 5 and 6 of Chapter IV apply from 16 November 2022.
# TODO: map Sections 4 to 6 of Chapter IV to article numbers and add them to unit_dates.
_DSA_EARLY = "applies from 16 November 2022; the rest of the Regulation from 17 February 2024."
DSA = Applicability(
    entry_into_force="2022-11-16",
    applies_from="2024-02-17",
    text_version="2022-10-27",
    definitions_unit="art_3",
    unit_dates={"art_43": "2022-11-16"},
    unit_notes={
        "art_24": "Article 24(2), (3) and (6) " + _DSA_EARLY,
        "art_33": "Article 33(3) to (6) " + _DSA_EARLY,
        "art_37": "Article 37(7) " + _DSA_EARLY,
        "art_40": "Article 40(13) " + _DSA_EARLY,
    },
)

BY_CELEX = {
    "32024R1689": AI_ACT,
    "32016R0679": GDPR,
    "32023R2854": DATA_ACT,
    "32022L2555": NIS2,
    "32022R2554": DORA,
    "32024R2847": CRA,
    "32022R2065": DSA,
}

_ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def document_fields(celex: str) -> dict:
    """The fields added to a document in documents.json."""
    a = BY_CELEX.get(celex)
    if a is None:
        return {"entry_into_force": None, "applies_from": None, "text_version": None, "definitions_unit": None,
                "note": None}
    return {
        "entry_into_force": a.entry_into_force,
        "applies_from": a.applies_from,
        "text_version": a.text_version,
        "definitions_unit": a.definitions_unit,
        "note": a.note,
    }


def unit_fields(celex: str, unit_id: str) -> tuple[str | None, str | None]:
    """(applies_from, applicability_note) of a unit; applies_from is None when it is the document's."""
    a = BY_CELEX.get(celex)
    if a is None:
        return None, None
    return a.unit_dates.get(unit_id), a.unit_notes.get(unit_id)


def problems() -> list[str]:
    """Malformed dates in the curated data."""
    found = []
    for celex, a in BY_CELEX.items():
        dates = [a.entry_into_force, a.applies_from, a.text_version, *a.unit_dates.values()]
        found += [f"{celex}: bad date {d!r}" for d in dates if not _ISO_DATE.match(d)]
    return found
