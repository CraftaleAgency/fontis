package eu.fontis.corpus;

/**
 * A citable unit of a legal document (article, recital or annex) in one language, as produced by
 * {@code fontis-bench corpus build}. The unit id (e.g. {@code art_5}) is the same in every language.
 */
public record CorpusUnit(
		String celex,
		String lang,
		String unitId,
		String unitType,
		String label,
		String heading,
		String body,
		String url) {
}
