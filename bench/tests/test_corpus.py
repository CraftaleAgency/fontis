from fontis_bench.corpus import parse

# Trimmed-down copy of the CELLAR XHTML structure.
SAMPLE = b"""<html><body>
<div class="eli-subdivision" id="rct_1">
  <table><tbody><tr>
    <td><p class="oj-normal">(1)</p></td>
    <td><p class="oj-normal">The purpose of this Regulation is to improve the internal market.</p></td>
  </tr></tbody></table>
</div>
<div class="eli-subdivision" id="art_5">
  <p class="oj-ti-art">Article 5</p>
  <div class="eli-title" id="art_5.tit_1"><p class="oj-sti-art">Prohibited AI practices</p></div>
  <div id="005.001">
    <p class="oj-normal">1.   The following AI practices shall be prohibited:</p>
    <table><tbody><tr>
      <td><p class="oj-normal">(a)</p></td>
      <td><p class="oj-normal">the placing on the market of manipulative AI systems;</p></td>
    </tr></tbody></table>
  </div>
</div>
<div class="eli-container" id="anx_III">
  <p class="oj-doc-ti">ANNEX III</p>
  <p class="oj-doc-ti">High-risk AI systems referred to in Article 6(2)</p>
  <p class="oj-normal">Biometrics, in so far as their use is permitted.</p>
</div>
</body></html>"""


def test_parse_units_and_ids():
    units = {u.unit_id: u for u in parse(SAMPLE, "32024R1689", "eng")}
    assert set(units) == {"rct_1", "art_5", "anx_III"}
    assert units["art_5"].unit_type == "article"
    assert units["art_5"].url.endswith("/EN/TXT/?uri=CELEX:32024R1689#art_5")


def test_article_label_heading_and_markers_merged():
    art = next(u for u in parse(SAMPLE, "32024R1689", "eng") if u.unit_id == "art_5")
    assert art.label == "Article 5"
    assert art.heading == "Prohibited AI practices"
    assert art.body.splitlines() == [
        "1. The following AI practices shall be prohibited:",
        "(a) the placing on the market of manipulative AI systems;",
    ]


def test_recital_number_stripped_from_body():
    rct = next(u for u in parse(SAMPLE, "32024R1689", "eng") if u.unit_id == "rct_1")
    assert rct.label == "(1)"
    assert rct.body == "The purpose of this Regulation is to improve the internal market."


def test_annex_titles():
    anx = next(u for u in parse(SAMPLE, "32024R1689", "eng") if u.unit_id == "anx_III")
    assert anx.label == "ANNEX III"
    assert anx.heading == "High-risk AI systems referred to in Article 6(2)"
    assert anx.body == "Biometrics, in so far as their use is permitted."
