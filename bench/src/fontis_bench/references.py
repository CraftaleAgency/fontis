"""Cross-references between units of the same act, extracted from the English text with rules.

"Article 6(2)", "Articles 6 to 8", "Articles 6, 7 and 9", "Annex III", "Annexes I and II" are references to the same
act unless they are followed by the name of another act ("of Regulation (EU) 2016/679", "of Directive ...", "TFEU").
Unit ids are the same in every language, so the references found in English apply to all of them.
"""

from __future__ import annotations

import re

_NUMBER = r"\d+[a-z]?(?:\(\w{1,4}\))*"
_ROMAN = r"[IVXLC]+[a-z]?"
_SEP = r"\s*(?:,|\band\b|\bor\b|\bto\b)\s*"

ARTICLES = re.compile(rf"\bArticles?\s+({_NUMBER}(?:{_SEP}{_NUMBER})*)")
ANNEXES = re.compile(rf"\bAnnex(?:es)?\s+({_ROMAN}(?:{_SEP}{_ROMAN})*)\b")

# What follows a reference to another act: "of Regulation (EU) 2016/679", "to Directive 2014/53/EU", "TFEU".
OTHER_ACT = re.compile(
    r"^[\s,]*(?:(?:first|second|third|fourth|fifth)\s+subparagraph,?\s*)?(?:point\s+\(\w+\),?\s*)?"
    r"(?:(?:of|in|to|under)\s+(?:the\s+)?(?:Regulation|Directive|Decision|Treaty|Charter|that\s+Regulation|"
    r"that\s+Directive|those\s+Regulations|those\s+Directives|Council|Implementing|Delegated|Protocol)\b"
    r"|TFEU\b|TEU\b)",
    re.IGNORECASE,
)

_ROMAN_VALUES = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100}


def _roman_to_int(roman: str) -> int:
    total = 0
    for i, ch in enumerate(roman):
        value = _ROMAN_VALUES[ch]
        total += -value if i + 1 < len(roman) and _ROMAN_VALUES[roman[i + 1]] > value else value
    return total


def _int_to_roman(n: int) -> str:
    out = ""
    for value, numeral in ((100, "C"), (90, "XC"), (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"),
                           (1, "I")):
        while n >= value:
            out += numeral
            n -= value
    return out


def _expand(items: str, item: str, to_int, to_str) -> list[str]:
    """Items of a list such as "6, 7 and 9" or "6 to 8"; ranges are expanded."""
    tokens = re.split(rf"({_SEP})", items)
    found: list[str] = []
    previous = None
    pending_range = False
    for token in tokens:
        if re.fullmatch(_SEP, token):
            pending_range = re.search(r"\bto\b", token) is not None
            continue
        match = re.match(item, token)
        if not match:
            continue
        base = re.match(r"\w+?(?=\(|$)", match.group(0)) or match
        value = base.group(0)
        if pending_range and previous is not None and re.fullmatch(r"\d+|[IVXLC]+", value):
            start, end = to_int(previous), to_int(value)
            if 0 < end - start <= 50:
                found += [to_str(n) for n in range(start + 1, end)]
        found.append(value)
        previous = value if re.fullmatch(r"\d+|[IVXLC]+", value) else None
        pending_range = False
    return found


def extract(text: str, own_id: str | None = None, existing: set[str] | None = None) -> list[str]:
    """Unit ids referred to in the text: articles, then annexes, each in numeric order.

    :param own_id: the unit's own id, never returned
    :param existing: unit ids of the act; references to anything else are dropped
    """
    refs: list[str] = []
    for pattern, prefix, item, to_int, to_str in (
        (ARTICLES, "art_", _NUMBER, int, str),
        (ANNEXES, "anx_", _ROMAN, _roman_to_int, _int_to_roman),
    ):
        for match in pattern.finditer(text):
            if OTHER_ACT.match(text[match.end():match.end() + 80]):
                continue
            for value in _expand(match.group(1), item, to_int, to_str):
                refs.append(prefix + value)
    seen = set()
    result = []
    for ref in refs:
        if ref == own_id or ref in seen or (existing is not None and ref not in existing):
            continue
        seen.add(ref)
        result.append(ref)
    return sorted(result, key=lambda r: (not r.startswith("art_"), _order(r)))


def _order(unit_id: str) -> tuple[int, str]:
    value = unit_id.split("_", 1)[1]
    digits = re.match(r"\d+", value)
    if digits:
        return int(digits.group(0)), value
    roman = re.match(r"[IVXLC]+", value)
    return (_roman_to_int(roman.group(0)) if roman else 0), value
