from fontis_bench.references import extract

EXISTING = {"art_3", "art_5", "art_6", "art_7", "art_8", "art_9", "art_43", "anx_I", "anx_II", "anx_III"}


def test_articles_with_paragraphs_lists_and_ranges():
    text = "As referred to in Article 6(2) and Articles 7 to 9, and in Article 43(1), point (a)."
    assert extract(text, "art_5", EXISTING) == ["art_6", "art_7", "art_8", "art_9", "art_43"]


def test_annexes_and_self_reference():
    text = "The systems listed in Annex III and in Annexes I and II, as provided in Article 5."
    assert extract(text, "art_5", EXISTING) == ["anx_I", "anx_II", "anx_III"]


def test_references_to_other_acts_are_left_out():
    text = (
        "Article 9 of Regulation (EU) 2016/679, Article 16 TFEU, Annex I to Directive 2006/42/EC "
        "and Article 3 of this Regulation."
    )
    assert extract(text, None, EXISTING) == ["art_3"]


def test_unknown_units_are_dropped():
    assert extract("See Article 200 and Annex XX.", None, EXISTING) == []
