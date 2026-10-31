package eu.fontis.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import eu.fontis.corpus.Chunker;
import eu.fontis.corpus.CorpusUnit;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.builder.MultiResourceItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Loads the corpus produced by {@code fontis-bench corpus build} into Postgres and embeds it:
 * documents → units → chunks with embeddings. Every step upserts, and the chunk step only reads
 * units that have no chunks yet, so the job can be re-run after a failure and resumes where it stopped.
 */
@Configuration(proxyBeanMethods = false)
class IngestJobConfig {

	static final int MAX_CHUNK_CHARS = 1200;

	// The corpus JSONL uses snake_case keys (unit_id, unit_type).
	private static final JsonMapper JSON = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	@Bean
	Job ingestCorpus(JobRepository jobRepository, Step documentsStep, Step unitsStep, Step chunksStep) {
		return new JobBuilder("ingestCorpus", jobRepository).incrementer(new RunIdIncrementer())
			.start(documentsStep)
			.next(unitsStep)
			.next(chunksStep)
			.build();
	}

	// ---------- Step 1: documents ----------

	@Bean
	Step documentsStep(JobRepository jobRepository, PlatformTransactionManager tx, JdbcTemplate jdbc,
			@Value("${fontis.ingest.corpus-dir}") String corpusDir) {
		return new StepBuilder("documentsStep", jobRepository).tasklet((contribution, context) -> {
			for (Map<String, String> doc : readDocuments(Path.of(corpusDir))) {
				jdbc.update("""
						INSERT INTO source_document (celex, short_name, title) VALUES (?, ?, ?)
						ON CONFLICT (celex) DO UPDATE SET short_name = EXCLUDED.short_name, title = EXCLUDED.title
						""", doc.get("celex"), doc.get("short_name"), doc.get("title"));
				contribution.incrementWriteCount(1);
			}
			return RepeatStatus.FINISHED;
		}, tx).build();
	}

	private static java.util.List<Map<String, String>> readDocuments(Path corpusDir) throws IOException {
		return JSON.readValue(Files.readString(corpusDir.resolve("documents.json")), new TypeReference<>() {
		});
	}

	// ---------- Step 2: units ----------

	@Bean
	Step unitsStep(JobRepository jobRepository, PlatformTransactionManager tx,
			MultiResourceItemReader<CorpusUnit> unitFileReader, JdbcBatchItemWriter<CorpusUnit> unitWriter) {
		return new StepBuilder("unitsStep", jobRepository).<CorpusUnit, CorpusUnit>chunk(500)
			.reader(unitFileReader)
			.writer(unitWriter)
			.transactionManager(tx)
			.build();
	}

	// Step-scoped: the corpus directory is read when the job runs, not when the application starts.
	@Bean
	@StepScope
	MultiResourceItemReader<CorpusUnit> unitFileReader(@Value("${fontis.ingest.corpus-dir}") String corpusDir)
			throws IOException {
		// A String, not a Path: in a web application Spring would resolve the Path as a servlet
		// resource and reject the relative "../data/corpus".
		Resource[] files;
		try (var paths = Files.list(Path.of(corpusDir))) {
			files = paths.filter(p -> p.toString().endsWith(".jsonl"))
				.sorted()
				.map(FileSystemResource::new)
				.toArray(Resource[]::new);
		}
		var delegate = new FlatFileItemReaderBuilder<CorpusUnit>().name("unitLineReader")
			.lineMapper((line, lineNumber) -> JSON.readValue(line, CorpusUnit.class))
			.build();
		return new MultiResourceItemReaderBuilder<CorpusUnit>().name("unitFileReader")
			.resources(files)
			.delegate(delegate)
			.build();
	}

	@Bean
	JdbcBatchItemWriter<CorpusUnit> unitWriter(javax.sql.DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<CorpusUnit>().dataSource(dataSource)
			.sql("""
					INSERT INTO source_unit (celex, lang, unit_id, unit_type, label, heading, body, url)
					VALUES (:celex, :lang, :unitId, :unitType, :label, :heading, :body, :url)
					ON CONFLICT (celex, lang, unit_id) DO UPDATE SET unit_type = EXCLUDED.unit_type,
					    label = EXCLUDED.label, heading = EXCLUDED.heading, body = EXCLUDED.body, url = EXCLUDED.url
					""")
			.itemSqlParameterSourceProvider(u -> new MapSqlParameterSource().addValue("celex", u.celex())
				.addValue("lang", u.lang())
				.addValue("unitId", u.unitId())
				.addValue("unitType", u.unitType())
				.addValue("label", u.label())
				.addValue("heading", u.heading())
				.addValue("body", u.body())
				.addValue("url", u.url()))
			.build();
	}

	// ---------- Step 3: chunks and embeddings ----------

	@Bean
	Step chunksStep(JobRepository jobRepository, PlatformTransactionManager tx,
			JdbcCursorItemReader<UnitWithDocument> unitsWithoutChunksReader, EmbeddingModel embeddingModel,
			JdbcTemplate jdbc, @Value("${fontis.ingest.units-per-batch}") int unitsPerBatch) {
		Chunker chunker = new Chunker(MAX_CHUNK_CHARS);
		return new StepBuilder("chunksStep", jobRepository).<UnitWithDocument, UnitChunks>chunk(unitsPerBatch)
			.reader(unitsWithoutChunksReader)
			.processor(item -> new UnitChunks(item.unit(), Chunker.header(item.documentName(), item.unit()),
					chunker.split(item.unit().body())))
			.writer(new EmbeddingChunkWriter(embeddingModel, jdbc))
			.transactionManager(tx)
			.build();
	}

	record UnitWithDocument(CorpusUnit unit, String documentName) {
	}

	@Bean
	JdbcCursorItemReader<UnitWithDocument> unitsWithoutChunksReader(javax.sql.DataSource dataSource) {
		return new JdbcCursorItemReaderBuilder<UnitWithDocument>().name("unitsWithoutChunksReader")
			.dataSource(dataSource)
			.sql("""
					SELECT u.*, d.short_name
					FROM source_unit u JOIN source_document d USING (celex)
					WHERE NOT EXISTS (SELECT 1 FROM chunk c
					                  WHERE c.celex = u.celex AND c.lang = u.lang AND c.unit_id = u.unit_id)
					ORDER BY u.celex, u.lang, u.unit_id
					""")
			.rowMapper((rs, rowNum) -> new UnitWithDocument(new CorpusUnit(rs.getString("celex"),
					rs.getString("lang"), rs.getString("unit_id"), rs.getString("unit_type"), rs.getString("label"),
					rs.getString("heading"), rs.getString("body"), rs.getString("url")), rs.getString("short_name")))
			.build();
	}

}
