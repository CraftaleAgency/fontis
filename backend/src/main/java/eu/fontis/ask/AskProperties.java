package eu.fontis.ask;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval and answering settings.
 *
 * @param candidates chunks taken from each of vector and full-text search before fusion
 * @param maxSources units given to the model as numbered sources
 * @param minSimilarity below this cosine similarity of the best chunk, no answer is attempted
 * @param numCtx context window for the chat model
 */
@ConfigurationProperties("fontis.ask")
public record AskProperties(int candidates, int maxSources, double minSimilarity, int numCtx) {
}
