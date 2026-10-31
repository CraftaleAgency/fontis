package eu.fontis.ingest;

import eu.fontis.FakeEmbeddingModelConfiguration;
import eu.fontis.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "fontis.ingest.corpus-dir=src/test/resources/corpus")
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
class IngestJobTests {

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job ingestCorpus;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void loadsUnitsAndEmbedsChunksIdempotently() throws Exception {
		assertThat(run()).isEqualTo(BatchStatus.COMPLETED);

		assertThat(count("source_document")).isEqualTo(1);
		assertThat(count("source_unit")).isEqualTo(4);
		// art_16 is ~3,500 characters: it must be split; the other units fit in one chunk each.
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM chunk WHERE unit_id = 'art_16'", Integer.class))
			.isGreaterThan(1);
		int chunks = count("chunk");
		assertThat(chunks).isGreaterThan(4);
		assertThat(this.jdbc.queryForObject("SELECT vector_dims(embedding) FROM chunk LIMIT 1", Integer.class))
			.isEqualTo(1024);
		// Language-specific full-text search works on the Italian chunk.
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM chunk WHERE lang = 'ita' AND tsv @@ to_tsquery('italian', 'vietata')",
				Integer.class))
			.isEqualTo(1);

		// A second run finds nothing left to embed and changes nothing.
		assertThat(run()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(count("chunk")).isEqualTo(chunks);
	}

	private BatchStatus run() throws Exception {
		var params = new JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters();
		return this.jobOperator.start(this.ingestCorpus, params).getStatus();
	}

	private int count(String table) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

}
