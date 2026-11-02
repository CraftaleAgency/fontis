package eu.fontis.ingest;

import java.util.ArrayList;
import java.util.List;

import com.pgvector.PGvector;
import eu.fontis.corpus.Chunker;
import eu.fontis.corpus.CorpusUnit;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Splits units into chunks, embeds every chunk of a batch in one model call and stores them.
 * Shared by the corpus ingestion job and document uploads. Upserts make it safe to repeat.
 */
@Component
public class ChunkEmbedder {

	static final int MAX_CHUNK_CHARS = 1200;

	private static final String UPSERT = """
			INSERT INTO chunk (celex, lang, unit_id, chunk_index, content, embedding)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT (celex, lang, unit_id, chunk_index)
			DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
			""";

	private final Chunker chunker = new Chunker(MAX_CHUNK_CHARS);

	private final EmbeddingModel embeddingModel;

	private final JdbcTemplate jdbc;

	ChunkEmbedder(EmbeddingModel embeddingModel, JdbcTemplate jdbc) {
		this.embeddingModel = embeddingModel;
		this.jdbc = jdbc;
	}

	/** A unit with the name of its document, used in the context line of each chunk. */
	public record UnitOfDocument(CorpusUnit unit, String documentName) {
	}

	UnitChunks split(UnitOfDocument item) {
		return new UnitChunks(item.unit(), Chunker.header(item.documentName(), item.unit()),
				this.chunker.split(item.unit().body()));
	}

	/** Splits, embeds and stores the units; returns the number of chunks written. */
	public int embed(List<UnitOfDocument> units) {
		return write(units.stream().map(this::split).toList());
	}

	int write(List<? extends UnitChunks> items) {
		List<String> inputs = new ArrayList<>();
		for (UnitChunks item : items) {
			inputs.addAll(item.embeddingInputs());
		}
		if (inputs.isEmpty()) {
			return 0;
		}
		List<float[]> embeddings = this.embeddingModel.embed(inputs);
		if (embeddings.size() != inputs.size()) {
			throw new IllegalStateException("Expected " + inputs.size() + " embeddings, got " + embeddings.size());
		}

		List<Object[]> rows = new ArrayList<>(inputs.size());
		int next = 0;
		for (UnitChunks item : items) {
			for (int i = 0; i < item.chunks().size(); i++) {
				rows.add(new Object[] { item.unit().celex(), item.unit().lang(), item.unit().unitId(), i,
						item.chunks().get(i), new PGvector(embeddings.get(next++)) });
			}
		}
		this.jdbc.batchUpdate(UPSERT, rows);
		return rows.size();
	}

}
