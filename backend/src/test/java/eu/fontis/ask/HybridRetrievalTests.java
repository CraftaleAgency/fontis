package eu.fontis.ask;

import java.util.List;

import eu.fontis.FakeEmbeddingModelConfiguration;
import eu.fontis.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hybrid search on the test corpus, with bag-of-words embeddings standing in for bge-m3: rank
 * fusion, de-duplication across languages and answer-language labels.
 */
@SpringBootTest(properties = "fontis.ingest.corpus-dir=src/test/resources/corpus")
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
class HybridRetrievalTests {

	@Autowired
	Retrieval retrieval;

	@Autowired
	JobOperator jobOperator;

	@Autowired
	Job ingestCorpus;

	@BeforeEach
	void ingest() throws Exception {
		this.jobOperator.start(this.ingestCorpus,
				new JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters());
	}

	@Test
	void fullTextMatchRanksTheRightArticleFirst() {
		List<Source> sources = this.retrieval.search("Which AI practices are prohibited?", "eng", 5).sources();

		assertThat(sources.get(0).unitId()).isEqualTo("art_5");
		assertThat(sources.get(0).title()).isEqualTo("AI Act — Article 5 — Prohibited AI practices");
	}

	@Test
	void eachUnitAppearsOnceAndIsLabelledInTheAnswerLanguage() {
		// art_5 exists in English and Italian: it must come back once, labelled in Italian.
		List<Source> sources = this.retrieval.search("Which AI practices are prohibited?", "ita", 5).sources();

		assertThat(sources).extracting(Source::unitId).doesNotHaveDuplicates();
		Source art5 = sources.stream().filter(s -> s.unitId().equals("art_5")).findFirst().orElseThrow();
		assertThat(art5.label()).isEqualTo("Articolo 5");
		assertThat(art5.url()).contains("/IT/");
	}

	@Test
	void unitsMissingInTheAnswerLanguageFallBackToTheMatchedLanguage() {
		// art_16 exists only in English in the fixture.
		Source art16 = this.retrieval.search("obligations of providers of high-risk AI systems", "ita", 5)
			.sources()
			.stream()
			.filter(s -> s.unitId().equals("art_16"))
			.findFirst()
			.orElseThrow();

		assertThat(art16.label()).isEqualTo("Article 16");
		assertThat(art16.matchedLang()).isEqualTo("eng");
	}

}
