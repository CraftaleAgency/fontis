package eu.fontis.documents;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import eu.fontis.FakeEmbeddingModelConfiguration;
import eu.fontis.TestcontainersConfiguration;
import eu.fontis.ask.Access;
import eu.fontis.ask.Retrieval;
import eu.fontis.ask.Source;
import eu.fontis.security.Users;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
class DocumentPermissionTests {

	private static final String QUESTION = "remote work allowance policy for employees";

	@Autowired
	Documents documents;

	@Autowired
	DocumentAccess access;

	@Autowired
	Retrieval retrieval;

	@Autowired
	Users users;

	@Autowired
	JdbcClient jdbc;

	String policyId;

	String noticeId;

	@BeforeEach
	void setUp() {
		this.jdbc.sql("DELETE FROM source_document WHERE kind = 'upload'").update();
		this.jdbc.sql("DELETE FROM app_user").update();
		this.jdbc.sql("DELETE FROM app_group").update();
		this.users.create("admin@example.eu", "Admin", "admin-password-123", Users.Role.ADMIN, List.of());
		this.users.create("alice@example.eu", "Alice", "alice-password-123", Users.Role.USER, List.of("legal"));
		this.users.create("bob@example.eu", "Bob", "bob-password-123", Users.Role.USER, List.of("sales"));
		String policy = "Remote work policy.\nEmployees receive a remote work allowance of 40 euros per month.";
		this.policyId = this.documents
			.add(new Documents.Upload("policy.txt", policy.getBytes(StandardCharsets.UTF_8), "Remote work policy",
					"eng", Documents.Visibility.RESTRICTED, List.of("legal"), null))
			.id();
		// Stands in for the EU legislation, which this test database does not load.
		this.noticeId = this.documents
			.add(new Documents.Upload("notice.txt", "Office opening hours: 9 to 18.".getBytes(StandardCharsets.UTF_8),
					"Public notice", "eng", Documents.Visibility.PUBLIC, List.of(), null))
			.id();
	}

	@Test
	void aRestrictedDocumentIsFoundOnlyByItsGroupsAndAdministrators() {
		assertThat(foundBy(user("alice@example.eu"))).as("member of legal").isTrue();
		assertThat(foundBy(user("admin@example.eu"))).as("administrator").isTrue();
		assertThat(foundBy(user("bob@example.eu"))).as("member of sales only").isFalse();
		assertThat(foundBy(anonymous())).as("anonymous").isFalse();
	}

	@Test
	void everyoneStillSearchesThePublicLegislation() {
		Access bob = this.access.of(user("bob@example.eu"));
		assertThat(bob.all()).isFalse();
		assertThat(bob.documents()).contains(this.noticeId).doesNotContain(this.policyId);
		assertThat(this.access.of(anonymous()).documents()).containsExactly(this.noticeId);
		assertThat(this.documents.visible(bob)).extracting(Documents.Info::id).doesNotContain(this.policyId);
		assertThat(this.documents.visible(this.access.of(user("alice@example.eu")))).extracting(Documents.Info::id)
			.contains(this.policyId);
	}

	@Test
	void aPdfIsSplitIntoPagesThatAnswersCanCite() throws Exception {
		Documents.Info pdf = this.documents.add(new Documents.Upload("handbook.pdf",
				pdf("Holiday rules: 26 days of paid leave.", "Expenses: keep every receipt."), "Handbook", "eng",
				Documents.Visibility.RESTRICTED, List.of("legal"), null));

		assertThat(pdf.units()).isEqualTo(2);
		assertThat(this.jdbc.sql("SELECT label FROM source_unit WHERE celex = :id ORDER BY unit_id")
			.param("id", pdf.id())
			.query(String.class)
			.list()).containsExactly("p. 1", "p. 2");
	}

	@Test
	void deletingADocumentRemovesItsChunks() {
		assertThat(this.documents.delete(this.policyId)).isTrue();
		assertThat(this.jdbc.sql("SELECT count(*) FROM chunk WHERE celex = :id")
			.param("id", this.policyId)
			.query(Integer.class)
			.single()).isZero();
		this.jdbc.sql("INSERT INTO source_document (celex, short_name, title) VALUES ('32016R0679', 'GDPR', 'GDPR')")
			.update();
		assertThat(this.documents.delete("32016R0679")).as("EU legislation cannot be deleted").isFalse();
		this.jdbc.sql("DELETE FROM source_document WHERE celex = '32016R0679'").update();
	}

	private boolean foundBy(Authentication authentication) {
		List<Source> sources = this.retrieval
			.search(QUESTION, "eng", 10, Retrieval.Options.of(this.access.of(authentication)))
			.sources();
		return sources.stream().anyMatch(s -> s.celex().equals(this.policyId));
	}

	private static Authentication user(String email) {
		return UsernamePasswordAuthenticationToken.authenticated(email, null, AuthorityUtils.NO_AUTHORITIES);
	}

	private static Authentication anonymous() {
		return new AnonymousAuthenticationToken("key", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
	}

	private static byte[] pdf(String... pages) throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (String text : pages) {
				PDPage page = new PDPage();
				document.addPage(page);
				try (PDPageContentStream content = new PDPageContentStream(document, page)) {
					content.beginText();
					content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
					content.newLineAtOffset(72, 700);
					content.showText(text);
					content.endText();
				}
			}
			document.save(out);
			return out.toByteArray();
		}
	}

}
