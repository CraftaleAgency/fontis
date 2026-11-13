package eu.fontis.ask;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval and answering settings.
 *
 * @param candidates chunks taken from each of vector and full-text search before fusion
 * @param maxSources units given to the model as numbered sources
 * @param minSimilarity below this cosine similarity of the best chunk, no answer is attempted
 * @param numCtx context window for the chat model
 * @param numPredict tokens the chat model may generate
 * @param maxConcurrent answers generated at the same time
 * @param answerTimeout time allowed for retrieval and generation of one answer
 * @param textWeight weight of full-text ranks in the fusion; vector ranks weigh 1
 * @param recitalWeight score multiplier for recitals
 * @param sumLanguages add up a unit's scores across languages instead of keeping the best
 * @param sourceChars characters given to the model for each source by rank, taken from several chunks of the
 * unit; the last value applies to the remaining sources
 */
@ConfigurationProperties("fontis.ask")
public record AskProperties(int candidates, int maxSources, double minSimilarity, int numCtx, int numPredict, int maxConcurrent, Duration answerTimeout,
		double textWeight,
		double recitalWeight, boolean sumLanguages, List<Integer> sourceChars) {

	/** Characters for the source at this rank (0 = best). */
	public int sourceChars(int rank) {
		return this.sourceChars.get(Math.min(rank, this.sourceChars.size() - 1));
	}

}
