from fontis_bench.support import answer_sentences, split_sentences


def test_sentences_with_markers_before_and_after_the_full_stop():
    answer = "Social scoring is prohibited [1]. Fines reach EUR 35 000 000 or 7 %. [2][3] See Art. 5 for details."
    assert split_sentences(answer) == [
        {"text": "Social scoring is prohibited.", "citations": [1]},
        {"text": "Fines reach EUR 35 000 000 or 7 %.", "citations": [2, 3]},
        {"text": "See Art. 5 for details.", "citations": []},
    ]


def test_lists_ranges_and_lines():
    answer = "Obligations:\n- notify within 24 hours [1, 3]\n- report within 72 hours [2-3]."
    assert split_sentences(answer) == [
        {"text": "Obligations:", "citations": []},
        {"text": "- notify within 24 hours", "citations": [1, 3]},
        {"text": "- report within 72 hours.", "citations": [2, 3]},
    ]


def test_verification_from_the_response_is_preferred():
    answer = {
        "answer": "Ignored [1].",
        "verification": {"sentences": [{"text": "Checked.", "citations": [2], "verdict": "SUPPORTED"}]},
    }
    assert answer_sentences(answer) == [{"text": "Checked.", "citations": [2], "system": "SUPPORTED"}]
