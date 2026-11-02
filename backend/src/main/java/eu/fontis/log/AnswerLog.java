package eu.fontis.log;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import eu.fontis.ask.AskResponse;
import eu.fontis.ask.AskResponse.Citation;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Record of every answer, for the AI Act's record-keeping and for review: question, outcome, cited
 * sources, model and timings. Questions may contain personal data, so no user identifier or IP
 * address is stored and entries are deleted after a retention period.
 */
@Component
public class AnswerLog {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcClient jdbc;

	private final int retentionDays;

	AnswerLog(JdbcClient jdbc, @Value("${fontis.log.retention-days}") int retentionDays) {
		this.jdbc = jdbc;
		this.retentionDays = retentionDays;
	}

	/**
	 * @param id log id
	 * @param createdAt when the question was answered
	 */
	public record Entry(long id, Instant createdAt, String question, String lang, AskResponse.Status status,
			String answer, List<Citation> citations, String model, long retrievalMs, long generationMs) {
	}

	public long record(AskResponse response, String lang) {
		return this.jdbc.sql("""
				INSERT INTO answer_log (question, lang, status, answer, citations, model, retrieval_ms, generation_ms)
				VALUES (:question, :lang, :status, :answer, CAST(:citations AS jsonb), :model, :retrievalMs, :generationMs)
				RETURNING id
				""")
			.param("question", response.question())
			.param("lang", lang)
			.param("status", response.status().name())
			.param("answer", response.answer())
			.param("citations", JSON.writeValueAsString(response.citations()))
			.param("model", response.model())
			.param("retrievalMs", response.retrievalMs())
			.param("generationMs", response.generationMs())
			.query(Long.class)
			.single();
	}

	/** Most recent entries first. */
	public List<Entry> recent(int limit) {
		return this.jdbc.sql("SELECT * FROM answer_log ORDER BY created_at DESC, id DESC LIMIT :limit")
			.param("limit", limit)
			.query((rs, rowNum) -> new Entry(rs.getLong("id"),
					rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("question"),
					rs.getString("lang"), AskResponse.Status.valueOf(rs.getString("status")), rs.getString("answer"),
					JSON.readValue(rs.getString("citations"), new TypeReference<List<Citation>>() {
					}), rs.getString("model"), rs.getLong("retrieval_ms"), rs.getLong("generation_ms")))
			.list();
	}

	/** Deletes entries older than the retention period; runs every night. */
	@Scheduled(cron = "${fontis.log.cleanup-cron}")
	public int deleteExpired() {
		return deleteOlderThan(Instant.now().minus(this.retentionDays, ChronoUnit.DAYS));
	}

	int deleteOlderThan(Instant cutoff) {
		return this.jdbc.sql("DELETE FROM answer_log WHERE created_at < :cutoff")
			.param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
			.update();
	}

}
