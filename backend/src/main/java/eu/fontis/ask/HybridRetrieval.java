package eu.fontis.ask;

import java.util.List;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Hybrid retrieval: vector search (pgvector, cosine) and full-text search (each chunk stemmed in
 * its own language, ranked by how many distinct question terms it contains) are fused with weighted
 * reciprocal rank fusion. The same article usually matches in several languages; only its best
 * chunk is kept, and it is labelled in the answer language. Recitals are down-weighted so that the
 * binding article ranks above the recital that explains it.
 * <p>
 * The weights come from {@link AskProperties}; the defaults were chosen with the benchmark, which
 * found that full-text search did not help on paraphrased questions (its weight is 0 by default).
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
			    LIMIT :vectorCandidates
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
			    LIMIT :textCandidates
			), fused AS (
			    -- Weighted reciprocal rank fusion, per chunk.
			    SELECT celex, lang, unit_id, chunk_index,
			           sum(weight / (:rrfK + rank)) AS score, max(similarity) AS similarity
			    FROM (SELECT *, 1.0 AS weight FROM vector_hits
			          UNION ALL SELECT *, :textWeight AS weight FROM text_hits) hits
			    GROUP BY celex, lang, unit_id, chunk_index
			), best_chunk AS (
			    -- The chunk shown and cited for each unit.
			    SELECT DISTINCT ON (celex, unit_id) *
			    FROM fused
			    ORDER BY celex, unit_id, score DESC
			), unit_scores AS (
			    -- A unit's score: its best language, or all its languages added up; recitals weighted.
			    SELECT celex, unit_id,
			           (CASE WHEN :sumLanguages THEN sum(lang_score) ELSE max(lang_score) END)
			           * (CASE WHEN starts_with(unit_id, 'rct_') THEN :recitalWeight ELSE 1.0 END) AS score
			    FROM (SELECT celex, unit_id, lang, max(score) AS lang_score
			          FROM fused GROUP BY celex, unit_id, lang) per_language
			    GROUP BY celex, unit_id
			)
			SELECT b.celex, b.unit_id, b.lang AS matched_lang, u.score, b.similarity, c.content,
			       d.short_name,
			       coalesce(answer.label, matched.label) AS label,
			       coalesce(answer.heading, matched.heading) AS heading,
			       coalesce(answer.url, matched.url) AS url
			FROM unit_scores u
			JOIN best_chunk b USING (celex, unit_id)
			JOIN chunk c USING (celex, lang, unit_id, chunk_index)
			JOIN source_document d USING (celex)
			JOIN source_unit matched ON matched.celex = b.celex AND matched.lang = b.lang AND matched.unit_id = b.unit_id
			LEFT JOIN source_unit answer ON answer.celex = b.celex AND answer.lang = :answerLang AND answer.unit_id = b.unit_id
			ORDER BY u.score DESC
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
	public Result search(String question, String answerLang, int limit, Options options) {
		Mode mode = options.mode() == null ? Mode.HYBRID : options.mode();
		double textWeight = options.textWeight() == null ? this.properties.textWeight() : options.textWeight();
		double recitalWeight = options.recitalWeight() == null ? this.properties.recitalWeight()
				: options.recitalWeight();
		boolean sumLanguages = options.sumLanguages() == null ? this.properties.sumLanguages()
				: options.sumLanguages();
		PGvector vec = new PGvector(this.embeddingModel.embed(question));
		int candidates = this.properties.candidates();
		List<Source> sources = this.jdbc.sql(SEARCH)
			.param("vec", vec)
			.param("question", question)
			.param("vectorCandidates", mode == Mode.TEXT ? 0 : candidates)
			// A zero text weight would only add zero scores: skip the full-text search entirely.
			.param("textCandidates", mode == Mode.VECTOR || (mode == Mode.HYBRID && textWeight == 0) ? 0 : candidates)
			.param("rrfK", RRF_K)
			.param("textWeight", textWeight)
			.param("recitalWeight", recitalWeight)
			.param("sumLanguages", sumLanguages)
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
