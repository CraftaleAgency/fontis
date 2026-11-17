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
 * @param verification sentence-by-sentence check of the answer against the cited text, or {@code null} when the
 * answer was not checked
 * @param missing what the question asks that the sources do not say (partial refusal), or {@code null}
 */
public record AskResponse(String question, Status status, String answer, List<Citation> citations,
		List<Citation> sources, String model, long retrievalMs, long generationMs, Verification verification,
		String missing) {

	public AskResponse(String question, Status status, String answer, List<Citation> citations,
			List<Citation> sources, String model, long retrievalMs, long generationMs) {
		this(question, status, answer, citations, sources, model, retrievalMs, generationMs, null, null);
	}

	/** The same response with the result of the check. */
	public AskResponse withVerification(Verification newVerification) {
		return new AskResponse(this.question, this.status, this.answer, this.citations, this.sources, this.model,
				this.retrievalMs, this.generationMs, newVerification, this.missing);
	}

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
	 * @param appliesFrom ISO date from which the unit applies, or {@code null} if unknown
	 * @param applicabilityNote English note on when the unit applies, or {@code null}
	 */
	public record Citation(int n, String celex, String unitId, String document, String label, String heading,
			String url, String matchedLang, String appliesFrom, String applicabilityNote) {

		public Citation(int n, String celex, String unitId, String document, String label, String heading,
				String url, String matchedLang) {
			this(n, celex, unitId, document, label, heading, url, matchedLang, null, null);
		}

		static Citation of(int n, Source source) {
			return new Citation(n, source.celex(), source.unitId(), source.document(), source.label(),
					source.heading(), source.url(), source.matchedLang(), source.appliesFrom(),
					source.applicabilityNote());
		}

	}

	/**
	 * How well the cited text supports each sentence of the answer.
	 *
	 * @param sentences the answer's sentences, in order
	 * @param supported sentences with verdict {@link Verdict#SUPPORTED}
	 * @param total sentences checked
	 */
	public record Verification(List<SentenceCheck> sentences, int supported, int total) {

		public static Verification of(List<SentenceCheck> sentences) {
			int supported = (int) sentences.stream().filter(s -> s.verdict() == Verdict.SUPPORTED).count();
			return new Verification(List.copyOf(sentences), supported, sentences.size());
		}

	}

	/**
	 * One sentence of the answer and the passage that supports it best.
	 *
	 * @param text the sentence, with its citation markers
	 * @param citations the citation numbers in the sentence
	 * @param n the cited source holding the best passage, or {@code null}
	 * @param pinpoint the best passage's place in that source, in the answer language (e.g. "Articolo 5(1)(a)"),
	 * or {@code null}
	 * @param passage the best passage, in the answer language when the source exists in it, or {@code null}
	 * @param similarity embedding similarity between the sentence and the passage, or {@code null}
	 * @param figuresMissing figures (amounts, percentages, periods, dates) in the sentence that the cited text does
	 * not contain
	 * @param verdict the outcome of the check
	 */
	public record SentenceCheck(String text, List<Integer> citations, Integer n, String pinpoint, String passage,
			Double similarity, List<String> figuresMissing, Verdict verdict) {
	}

	public enum Verdict {

		/** A cited passage is close to the sentence. */
		SUPPORTED,

		/** The closest cited passage is related but not clearly the same statement. */
		WEAK,

		/** No cited passage is close to the sentence. */
		UNSUPPORTED,

		/** The sentence states a figure that the cited text does not contain. */
		FIGURE_MISMATCH,

		/** The sentence cites no source. */
		UNCITED

	}

}
