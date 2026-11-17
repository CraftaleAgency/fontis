package eu.fontis.corpus;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * A citable unit of a legal document (article, recital or annex) in one language, as produced by
 * {@code fontis-bench corpus build}. The unit id (e.g. {@code art_5}) is the same in every language.
 *
 * @param references unit ids of the same act that this unit refers to (e.g. {@code art_3}), never {@code null}
 * @param appliesFrom ISO date from which the unit applies, or {@code null} when it applies from the act's date
 * @param applicabilityNote English note on when or how the unit applies (e.g. a later deferral), or {@code null}
 */
public record CorpusUnit(
		String celex,
		String lang,
		String unitId,
		String unitType,
		String label,
		String heading,
		String body,
		String url,
		List<String> references,
		String appliesFrom,
		String applicabilityNote) {

	// The canonical constructor reads the corpus JSONL; the shorter one below is for uploads.
	@JsonCreator
	public CorpusUnit {
		references = references == null ? List.of() : List.copyOf(references);
	}

	/** A unit without references or applicability data, e.g. a page of an uploaded document. */
	public CorpusUnit(String celex, String lang, String unitId, String unitType, String label, String heading,
			String body, String url) {
		this(celex, lang, unitId, unitType, label, heading, body, url, List.of(), null, null);
	}

}
