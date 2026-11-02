package eu.fontis.log;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import eu.fontis.FakeEmbeddingModelConfiguration;
import eu.fontis.TestcontainersConfiguration;
import eu.fontis.ask.AskResponse;
import eu.fontis.ask.AskResponse.Citation;
import eu.fontis.ask.AskResponse.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
class AnswerLogTests {

	@Autowired
	AnswerLog log;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clear() {
		this.jdbc.sql("DELETE FROM answer_log").update();
	}

	@Test
	void recordsAnswersWithCitationsAndListsTheMostRecentFirst() {
		Citation art17 = new Citation(1, "32016R0679", "art_17", "GDPR", "Articolo 17", "Diritto alla cancellazione",
				"https://eur-lex.europa.eu/legal-content/IT/TXT/?uri=CELEX:32016R0679#art_17", "deu");
		this.log.record(new AskResponse("Posso far cancellare i miei dati?", Status.ANSWERED, "Sì [1].",
				List.of(art17), List.of(art17), "qwen3:14b", 500, 6000), "ita");
		this.log.record(new AskResponse("Chi ha vinto i mondiali?", Status.NO_SOURCE, null, List.of(), List.of(), null,
				400, 0), "ita");

		List<AnswerLog.Entry> entries = this.log.recent(10);

		assertThat(entries).extracting(AnswerLog.Entry::status).containsExactly(Status.NO_SOURCE, Status.ANSWERED);
		AnswerLog.Entry answered = entries.get(1);
		assertThat(answered.citations()).containsExactly(art17);
		assertThat(answered.model()).isEqualTo("qwen3:14b");
		assertThat(answered.lang()).isEqualTo("ita");
	}

	@Test
	void entriesOlderThanTheCutoffAreDeleted() {
		this.log.record(new AskResponse("old", Status.NO_SOURCE, null, List.of(), List.of(), null, 1, 0), "eng");
		this.jdbc.sql("UPDATE answer_log SET created_at = now() - interval '40 days'").update();
		this.log.record(new AskResponse("new", Status.NO_SOURCE, null, List.of(), List.of(), null, 1, 0), "eng");

		int deleted = this.log.deleteOlderThan(Instant.now().minus(30, ChronoUnit.DAYS));

		assertThat(deleted).isEqualTo(1);
		assertThat(this.log.recent(10)).extracting(AnswerLog.Entry::question).containsExactly("new");
	}

}
