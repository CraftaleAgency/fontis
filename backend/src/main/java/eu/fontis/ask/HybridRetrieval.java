package eu.fontis.ask;

import java.util.List;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Hybrid retrieval: vector search (pgvector, cosine) and full-text search (each chunk stemmed in
 * its own language, ranked by how many distinct question terms it contains) are fused with
 * reciprocal rank fusion. The same article usually matches in several languages; only its best
 * chunk is kept, and it is labelled in the answer language.
 */
@Component
class HybridRetrieval implements Retrieval {

	// Standard RRF constant: dampens the weight of top ranks so neither list dominates.
	private static final int RRF_K = 60;

	private static final String SEARCH = """
			WITH vector_hits AS (
			    SELECT celex, lang, unit_id, chunk_index,
			           1 - (embedding <=> :vec) AS similarity,
			           row_number() OVER (ORDER BY embedding <=> :vec) AS rank
			    FROM chunk
			    ORDER BY embedding <=> :vec
			    LIMIT :candidates
			), text_hits AS (
			    -- Distinct question terms matched first, then term density.
			    SELECT celex, lang, unit_id, chunk_index, NULL::float8 AS similarity,
			           row_number() OVER (ORDER BY matched DESC, ts_rank_cd(tsv, q, 1) DESC) AS rank
			    FROM chunk,
			         LATERAL (SELECT fontis_or_query(chunk.lang, :question) AS q,
			                         fontis_query_lexemes(chunk.lang, :question) AS lexemes) query,
			         LATERAL (SELECT count(*) AS matched
			                  FROM unnest(tsvector_to_array(tsv)) AS lexeme
			                  WHERE lexeme = ANY (lexemes)) coverage
			    WHERE tsv @@ q
			    ORDER BY rank
			    LIMIT :candidates
			), fused AS (
			    SELECT celex, lang, unit_id, chunk_index,
			           sum(1.0 / (:rrfK + rank)) AS score, max(similarity) AS similarity
			    FROM (SELECT * FROM vector_hits UNION ALL SELECT * FROM text_hits) hits
			    GROUP BY celex, lang, unit_id, chunk_index
			), best_per_unit AS (
			    SELECT DISTINCT ON (celex, unit_id) *
			    FROM fused
			    ORDER BY celex, unit_id, score DESC
			)
			SELECT b.celex, b.unit_id, b.lang AS matched_lang, b.score, b.similarity, c.content,
			       d.short_name,
			       coalesce(answer.label, matched.label) AS label,
			       coalesce(answer.heading, matched.heading) AS heading,
			       coalesce(answer.url, matched.url) AS url
			FROM best_per_unit b
			JOIN chunk c USING (celex, lang, unit_id, chunk_index)
			JOIN source_document d USING (celex)
			JOIN source_unit matched ON matched.celex = b.celex AND matched.lang = b.lang AND matched.unit_id = b.unit_id
			LEFT JOIN source_unit answer ON answer.celex = b.celex AND answer.lang = :answerLang AND answer.unit_id = b.unit_id
			ORDER BY b.score DESC
			LIMIT :limit
			""";

	private static final String BEST_SIMILARITY = "SELECT 1 - min(embedding <=> :vec) FROM chunk";

	private final EmbeddingModel embeddingModel;

	private final JdbcClient jdbc;

	private final AskProperties properties;

	HybridRetrieval(EmbeddingModel embeddingModel, JdbcClient jdbc, AskProperties properties) {
		this.embeddingModel = embeddingModel;
		this.jdbc = jdbc;
		this.properties = properties;
	}

	@Override
	public Result search(String question, String answerLang, int limit) {
		PGvector vec = new PGvector(this.embeddingModel.embed(question));
		List<Source> sources = this.jdbc.sql(SEARCH)
			.param("vec", vec)
			.param("question", question)
			.param("candidates", this.properties.candidates())
			.param("rrfK", RRF_K)
			.param("answerLang", answerLang)
			.param("limit", limit)
			.query((rs, rowNum) -> new Source(rs.getString("celex"), rs.getString("unit_id"),
					rs.getString("short_name"), rs.getString("label"), rs.getString("heading"), rs.getString("url"),
					rs.getString("matched_lang"), rs.getString("content"), rs.getDouble("score"),
					(Double) rs.getObject("similarity")))
			.list();
		Double best = this.jdbc.sql(BEST_SIMILARITY).param("vec", vec).query(Double.class).optional().orElse(0.0);
		return new Result(sources, best);
	}

}
