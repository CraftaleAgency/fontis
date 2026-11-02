package eu.fontis.ask;

import java.util.List;

/** Finds the units most relevant to a question. */
public interface Retrieval {

	/**
	 * @param question the question, in any supported language
	 * @param answerLang language for labels and links of the returned sources (e.g. {@code ita})
	 * @param limit maximum number of units
	 * @param options search mode and ranking weights; {@code null} fields use the configured defaults
	 */
	Result search(String question, String answerLang, int limit, Options options);

	default Result search(String question, String answerLang, int limit) {
		return search(question, answerLang, limit, Options.DEFAULTS);
	}

	/**
	 * The sources with the text to give the model, at most {@code budgetChars} characters each.
	 * By default each source keeps its best chunk.
	 */
	default List<Source> contexts(List<Source> sources, int budgetChars) {
		return sources;
	}

	/**
	 * Overrides for evaluation. {@code null} means "use the configured value".
	 *
	 * @param mode which searches to run
	 * @param textWeight weight of full-text ranks in the fusion (vector ranks weigh 1)
	 * @param recitalWeight multiplier for recitals, which explain articles but are not binding
	 * @param sumLanguages add up a unit's scores across languages instead of keeping the best
	 * @param access documents the search may return; {@code null} means every document
	 */
	record Options(Mode mode, Double textWeight, Double recitalWeight, Boolean sumLanguages, Access access) {

		public static final Options DEFAULTS = new Options(Mode.HYBRID, null, null, null, null);

		public static Options of(Mode mode) {
			return new Options(mode, null, null, null, null);
		}

		public static Options of(Access access) {
			return new Options(Mode.HYBRID, null, null, null, access);
		}

	}

	/** Vector and full-text search fused, or either alone (for evaluation). */
	enum Mode {

		HYBRID, VECTOR, TEXT

	}

	/**
	 * @param sources units, best first, at most one per unit across languages
	 * @param bestSimilarity cosine similarity of the closest chunk overall, in any language
	 */
	record Result(List<Source> sources, double bestSimilarity) {
	}

}
