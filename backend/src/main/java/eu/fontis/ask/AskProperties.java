package eu.fontis.ask;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval and answering settings.
 *
 * @param candidates chunks taken from each of vector and full-text search before fusion
 * @param maxSources units given to the model as numbered sources
 * @param minSimilarity below this cosine similarity of the best chunk, no answer is attempted
 * @param numCtx context window for the chat model
 * @param textWeight weight of full-text ranks in the fusion; vector ranks weigh 1
 * @param recitalWeight score multiplier for recitals
 * @param sumLanguages add up a unit's scores across languages instead of keeping the best
 * @param maxSourceChars characters of each source given to the model, taken from several chunks of the unit
 */
@ConfigurationProperties("fontis.ask")
public record AskProperties(int candidates, int maxSources, double minSimilarity, int numCtx, double textWeight,
		double recitalWeight, boolean sumLanguages, int maxSourceChars) {
}
