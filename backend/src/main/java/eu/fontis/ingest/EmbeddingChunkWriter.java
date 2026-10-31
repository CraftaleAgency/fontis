package eu.fontis.ingest;

import java.util.ArrayList;
import java.util.List;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Embeds every chunk of a batch of units in a single model call, then stores the chunks.
 * Upserts make the step safe to re-run.
 */
class EmbeddingChunkWriter implements ItemWriter<UnitChunks> {

	private static final String UPSERT = """
			INSERT INTO chunk (celex, lang, unit_id, chunk_index, content, embedding)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT (celex, lang, unit_id, chunk_index)
			DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
			""";

	private final EmbeddingModel embeddingModel;

	private final JdbcTemplate jdbc;

	EmbeddingChunkWriter(EmbeddingModel embeddingModel, JdbcTemplate jdbc) {
		this.embeddingModel = embeddingModel;
		this.jdbc = jdbc;
	}

	@Override
	public void write(Chunk<? extends UnitChunks> items) {
		List<String> inputs = new ArrayList<>();
		for (UnitChunks item : items) {
			inputs.addAll(item.embeddingInputs());
		}
		List<float[]> embeddings = this.embeddingModel.embed(inputs);
		if (embeddings.size() != inputs.size()) {
			throw new IllegalStateException(
					"Expected " + inputs.size() + " embeddings, got " + embeddings.size());
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
	}

}
