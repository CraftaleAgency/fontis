package eu.fontis.ask;

import java.util.List;

/** Finds the units most relevant to a question. */
public interface Retrieval {

	/**
	 * @param question the question, in any supported language
	 * @param answerLang language for labels and links of the returned sources (e.g. {@code ita})
	 * @param limit maximum number of units
	 */
	Result search(String question, String answerLang, int limit);

	/**
	 * @param sources units, best first, at most one per unit across languages
	 * @param bestSimilarity cosine similarity of the closest chunk overall, in any language
	 */
	record Result(List<Source> sources, double bestSimilarity) {
	}

}
