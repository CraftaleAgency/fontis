package eu.fontis.documents;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import eu.fontis.ask.Access;
import eu.fontis.corpus.Chunker;
import eu.fontis.corpus.CorpusUnit;
import eu.fontis.ingest.ChunkEmbedder;
import eu.fontis.ingest.ChunkEmbedder.UnitOfDocument;
import eu.fontis.security.Users;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documents uploaded by an organisation, searched next to the EU legislation. A PDF becomes one
 * unit per page ("p. 12"), so answers cite the page; other formats (Word, text, HTML...) are read
 * with Apache Tika and split into sections. Units are then chunked and embedded like the corpus.
 */
@Component
public class Documents {

	public static final Set<String> LANGUAGES = Set.of("eng", "ita", "deu", "fra", "spa");

	public enum Visibility {

		/** Everyone, signed in or not. */
		PUBLIC,

		/** Administrators and the groups the document is shared with. */
		RESTRICTED

	}

	/**
	 * @param id document id ("doc_..." for uploads, a CELEX number for EU legislation)
	 * @param kind {@code eu-law} or {@code upload}
	 */
	public record Info(String id, String title, String kind, Visibility visibility, String lang, String filename,
			List<String> groups, int units, int chunks, Instant createdAt) {
	}

	/** A file to add. */
	public record Upload(String filename, byte[] content, String title, String lang, Visibility visibility,
			List<String> groups, Long uploadedBy) {
	}

	private final JdbcClient jdbc;

	private final ChunkEmbedder embedder;

	private final Users users;

	private final Chunker sections;

	Documents(JdbcClient jdbc, ChunkEmbedder embedder, Users users,
			@Value("${fontis.documents.section-chars}") int sectionChars) {
		this.jdbc = jdbc;
		this.embedder = embedder;
		this.users = users;
		this.sections = new Chunker(sectionChars);
	}

	@Transactional
	public Info add(Upload upload) {
		String id = "doc_" + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong()).substring(0, 12);
		String title = upload.title() == null || upload.title().isBlank() ? upload.filename() : upload.title().strip();

		List<CorpusUnit> units = split(id, upload);
		if (units.isEmpty()) {
			throw new IllegalArgumentException("No text could be extracted from " + upload.filename());
		}

		this.jdbc.sql("""
				INSERT INTO source_document (celex, short_name, title, kind, visibility, lang, filename, uploaded_by)
				VALUES (:id, :title, :title, 'upload', :visibility, :lang, :filename, :uploadedBy)
				""")
			.param("id", id)
			.param("title", title)
			.param("visibility", upload.visibility().name().toLowerCase(Locale.ROOT))
			.param("lang", upload.lang())
			.param("filename", upload.filename())
			.param("uploadedBy", upload.uploadedBy())
			.update();
		for (long groupId : this.users.groupIds(upload.groups())) {
			this.jdbc.sql("INSERT INTO document_group (celex, group_id) VALUES (:id, :group)")
				.param("id", id)
				.param("group", groupId)
				.update();
		}
		for (CorpusUnit unit : units) {
			this.jdbc.sql("""
					INSERT INTO source_unit (celex, lang, unit_id, unit_type, label, heading, body, url)
					VALUES (:celex, :lang, :unitId, :unitType, :label, NULL, :body, '')
					""")
				.param("celex", unit.celex())
				.param("lang", unit.lang())
				.param("unitId", unit.unitId())
				.param("unitType", unit.unitType())
				.param("label", unit.label())
				.param("body", unit.body())
				.update();
		}
		this.embedder.embed(units.stream().map(unit -> new UnitOfDocument(unit, title)).toList());
		return find(id).orElseThrow();
	}

	/** Pages of a PDF, or sections of any other format. */
	List<CorpusUnit> split(String id, Upload upload) {
		Resource resource = new ByteArrayResource(upload.content()) {
			@Override
			public String getFilename() {
				return upload.filename();
			}
		};
		List<CorpusUnit> units = new ArrayList<>();
		if (upload.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			List<Document> pages = new PagePdfDocumentReader(resource).get();
			for (int i = 0; i < pages.size(); i++) {
				String text = pages.get(i).getText();
				Object pageNumber = pages.get(i).getMetadata().getOrDefault("page_number", i + 1);
				if (text != null && !text.isBlank()) {
					units.add(new CorpusUnit(id, upload.lang(), "p_" + pageNumber, "page", "p. " + pageNumber, null,
							text.strip(), ""));
				}
			}
		}
		else {
			String text = new TikaDocumentReader(resource).get()
				.stream()
				.map(Document::getText)
				.filter(t -> t != null && !t.isBlank())
				.reduce("", (a, b) -> a + "\n" + b);
			List<String> parts = this.sections.split(text);
			for (int i = 0; i < parts.size(); i++) {
				units.add(new CorpusUnit(id, upload.lang(), "s_" + (i + 1), "section", "§ " + (i + 1), null,
						parts.get(i), ""));
			}
		}
		return units;
	}

	public List<Info> visible(Access access) {
		return this.jdbc.sql(SELECT_INFO + """
				WHERE :allDocuments OR d.celex = ANY (:documents)
				GROUP BY d.celex ORDER BY d.kind DESC, d.created_at DESC, d.celex
				""")
			.param("allDocuments", access.all())
			.param("documents", access.documents().toArray(String[]::new))
			.query(this::info)
			.list();
	}

	public java.util.Optional<Info> find(String id) {
		return this.jdbc.sql(SELECT_INFO + " WHERE d.celex = :id GROUP BY d.celex")
			.param("id", id)
			.query(this::info)
			.optional();
	}

	/** Deletes an uploaded document with its units and chunks; EU legislation cannot be deleted. */
	public boolean delete(String id) {
		return this.jdbc.sql("DELETE FROM source_document WHERE celex = :id AND kind = 'upload'")
			.param("id", id)
			.update() == 1;
	}

	private static final String SELECT_INFO = """
			SELECT d.celex, d.title, d.kind, d.visibility, d.lang, d.filename, d.created_at,
			       coalesce((SELECT array_agg(g.name ORDER BY g.name) FROM document_group dg
			                 JOIN app_group g ON g.id = dg.group_id WHERE dg.celex = d.celex), '{}') AS groups,
			       (SELECT count(*) FROM source_unit u WHERE u.celex = d.celex) AS units,
			       (SELECT count(*) FROM chunk c WHERE c.celex = d.celex) AS chunks
			FROM source_document d
			""";

	private Info info(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new Info(rs.getString("celex"), rs.getString("title"), rs.getString("kind"),
				Visibility.valueOf(rs.getString("visibility").toUpperCase(Locale.ROOT)), rs.getString("lang"),
				rs.getString("filename"), List.of((String[]) rs.getArray("groups").getArray()), rs.getInt("units"),
				rs.getInt("chunks"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

}
