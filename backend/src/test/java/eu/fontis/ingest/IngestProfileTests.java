package eu.fontis.ingest;

import eu.fontis.FakeEmbeddingModelConfiguration;
import eu.fontis.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "ingest" profile runs the application without a web server (deploy/ingest.sh): the context
 * must start without web-only beans such as the security filter chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.main.web-application-type=none", "spring.batch.job.enabled=false" })
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
class IngestProfileTests {

	@Autowired
	Job ingestCorpus;

	@Test
	void startsWithoutAWebServer() {
		assertThat(this.ingestCorpus.getName()).isEqualTo("ingestCorpus");
	}

}
