package eu.fontis.ask;

import java.util.List;

/**
 * The answer to a question with the sources it cites.
 *
 * @param question the question as asked
 * @param status outcome, see {@link Status}
 * @param answer the answer text with [n] citation markers, or {@code null} when there is none
 * @param citations the sources cited in the answer, in citation-number order
 * @param sources every source given to the model; {@code citations} is a subset
 * @param model chat model that wrote the answer, or {@code null} if it was not called
 * @param retrievalMs time spent on retrieval
 * @param generationMs time spent on generation, 0 if the model was not called
 */
public record AskResponse(String question, Status status, String answer, List<Citation> citations,
		List<Citation> sources, String model, long retrievalMs, long generationMs) {

	public enum Status {

		/** Answered, and every claim is backed by cited sources. */
		ANSWERED,

		/** Retrieval found nothing close enough; the model was not called. */
		NO_SOURCE,

		/** The model judged that the sources do not contain the answer. */
		NO_ANSWER,

		/** The model answered without citing any source: the answer is not shown as reliable. */
		UNCITED

	}

	/**
	 * A source as shown to the user.
	 *
	 * @param n citation number used in the answer ([n])
	 */
	public record Citation(int n, String celex, String unitId, String document, String label, String heading,
			String url, String matchedLang) {

		static Citation of(int n, Source source) {
			return new Citation(n, source.celex(), source.unitId(), source.document(), source.label(),
					source.heading(), source.url(), source.matchedLang());
		}

	}

}
