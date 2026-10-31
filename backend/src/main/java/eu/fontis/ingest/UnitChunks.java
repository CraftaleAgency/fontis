package eu.fontis.ingest;

import java.util.List;

import eu.fontis.corpus.CorpusUnit;

/**
 * The chunks of one unit, ready to be embedded. {@code header} gives the embedding model the
 * context the chunk text alone lacks (document, article number and title).
 */
record UnitChunks(CorpusUnit unit, String header, List<String> chunks) {

	List<String> embeddingInputs() {
		return this.chunks.stream().map(chunk -> this.header + "\n" + chunk).toList();
	}

}
