package eu.fontis.ask;

/**
 * A unit retrieved for a question. Label, heading and URL are in the language requested for the
 * answer; {@code matchedLang} and {@code content} are those of the chunk that actually matched.
 *
 * @param celex document CELEX number
 * @param unitId unit id, the same in every language (e.g. {@code art_17})
 * @param document short document name (e.g. GDPR)
 * @param label unit label in the answer language (e.g. "Articolo 17")
 * @param heading unit title in the answer language, if any
 * @param url EUR-Lex link to the unit in the answer language
 * @param matchedLang language of the best-matching chunk
 * @param content text of the best-matching chunk
 * @param score fused rank score (reciprocal rank fusion)
 * @param similarity cosine similarity of the best chunk, or {@code null} if it matched only by full text
 */
public record Source(String celex, String unitId, String document, String label, String heading, String url,
		String matchedLang, String content, double score, Double similarity) {

	/** One line naming the source, e.g. "GDPR — Articolo 17 — Diritto alla cancellazione". */
	public String title() {
		return this.heading == null || this.heading.isBlank() ? this.document + " — " + this.label
				: this.document + " — " + this.label + " — " + this.heading;
	}

}
