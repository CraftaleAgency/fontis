package eu.fontis.mcp;

import java.util.List;
import java.util.Set;

import eu.fontis.ask.Access;
import eu.fontis.ask.AskProperties;
import eu.fontis.ask.Retrieval;
import eu.fontis.ask.Source;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fontis' retrieval over EU legislation as tools for any MCP client (Claude, IDE assistants, agents):
 * search with citations, read a unit, list the acts. Only public EU legislation is reachable, never
 * uploaded documents, and nothing is generated: the client's own model writes the answer from the
 * cited text.
 */
@Component
public class EuLawTools {

	static final Set<String> LANGUAGES = Set.of("eng", "ita", "deu", "fra", "spa");

	static final String NOTICE = "Texts as published in the Official Journal of the EU; later amendments are not "
			+ "included unless a note says so. Cite the article and link, and do not present this as legal advice.";

	private static final String PUBLIC_ACTS = """
			SELECT celex FROM source_document WHERE kind = 'eu-law' AND visibility = 'public'
			""";

	private static final String ACTS = """
			SELECT d.celex, d.short_name, d.title, d.entry_into_force::text AS entry_into_force,
			       d.applies_from::text AS applies_from, d.text_version::text AS text_version, d.note,
			       (SELECT count(*) FROM source_unit u WHERE u.celex = d.celex AND u.lang = 'eng') AS units
			FROM source_document d
			WHERE d.kind = 'eu-law' AND d.visibility = 'public'
			ORDER BY d.short_name
			""";

	private static final String UNIT = """
			SELECT u.celex, d.short_name, u.unit_id, u.unit_type, u.label, u.heading, u.body, u.url, u.refs,
			       coalesce(u.applies_from, d.applies_from)::text AS applies_from, u.applicability_note
			FROM source_unit u JOIN source_document d USING (celex)
			WHERE d.kind = 'eu-law' AND d.visibility = 'public'
			  AND (u.celex = :act OR lower(d.short_name) = lower(:act))
			  AND u.unit_id = :unitId AND u.lang = :lang
			""";

	private final Retrieval retrieval;

	private final JdbcClient jdbc;

	private final AskProperties properties;

	private final McpProperties mcp;

	EuLawTools(Retrieval retrieval, JdbcClient jdbc, AskProperties properties, McpProperties mcp) {
		this.retrieval = retrieval;
		this.jdbc = jdbc;
		this.properties = properties;
		this.mcp = mcp;
	}

	/**
	 * @param relevant whether the closest passage is close enough to answer from; when false, say the corpus does
	 * not cover the question
	 * @param results units, best first
	 */
	public record SearchResult(boolean relevant, List<Hit> results, String notice) {
	}

	/**
	 * @param citation how to cite the unit, e.g. "GDPR, Articolo 17 — Diritto alla cancellazione"
	 * @param foundIn language of the text below; it may differ from the requested language
	 * @param text the passages of the unit that match the question, with "[…]" where text was skipped
	 */
	public record Hit(String citation, String celex, String unitId, String url, String appliesFrom,
			String applicabilityNote, String foundIn, String text) {
	}

	public record Unit(String citation, String celex, String unitId, String unitType, String url, String appliesFrom,
			String applicabilityNote, List<String> references, String text, String notice) {
	}

	public record Act(String celex, String name, String title, String entryIntoForce, String appliesFrom,
			String textVersion, String note, long units) {
	}

	@Tool(name = "search_eu_law", description = """
			Search EU digital legislation (AI Act, GDPR, Data Act, NIS2, DORA, Cyber Resilience Act, Digital \
			Services Act) for the articles, recitals and annexes that answer a question, in any of English, Italian, \
			German, French or Spanish. Returns the matching text with the exact article to cite, a EUR-Lex link and \
			the date from which it applies. Answer only from the returned text and cite it; if 'relevant' is false, \
			say that this legislation does not cover the question.""")
	public SearchResult search(@ToolParam(description = "The question, in any supported language") String question,
			@ToolParam(required = false,
					description = "Language for labels and links: eng, ita, deu, fra or spa (default eng)") String lang,
			@ToolParam(required = false, description = "Maximum number of units, 1 to 10 (default 5)") Integer limit) {
		String language = language(lang);
		int n = limit == null ? 5 : Math.clamp(limit, 1, 10);
		Access access = Access.only(this.jdbc.sql(PUBLIC_ACTS).query(String.class).list());
		Retrieval.Result result = this.retrieval.search(question.strip(), language, n, Retrieval.Options.of(access));
		List<Hit> hits = this.retrieval.contexts(result.sources(), this.mcp.charsPerResult())
			.stream()
			.map(EuLawTools::hit)
			.toList();
		return new SearchResult(result.bestSimilarity() >= this.properties.minSimilarity(), hits, NOTICE);
	}

	@Tool(name = "get_eu_law_unit", description = """
			Read the full text of one article, recital or annex of an EU act, e.g. act "GDPR" or "32016R0679" and \
			unitId "art_17" (article 17), "rct_65" (recital 65) or "anx_III" (Annex III). Also returns the units it \
			refers to and the date from which it applies.""")
	public Unit unit(@ToolParam(description = "Short name (e.g. AI Act, GDPR) or CELEX number of the act") String act,
			@ToolParam(description = "Unit id: art_<n>, rct_<n> or anx_<roman>") String unitId,
			@ToolParam(required = false, description = "eng, ita, deu, fra or spa (default eng)") String lang) {
		return this.jdbc.sql(UNIT)
			.param("act", act.strip())
			.param("unitId", unitId.strip())
			.param("lang", language(lang))
			.query((rs, rowNum) -> {
				String heading = rs.getString("heading");
				String citation = rs.getString("short_name") + ", " + rs.getString("label")
						+ (heading == null || heading.isBlank() ? "" : " — " + heading);
				return new Unit(citation, rs.getString("celex"), rs.getString("unit_id"), rs.getString("unit_type"),
						rs.getString("url"), rs.getString("applies_from"), rs.getString("applicability_note"),
						List.of((String[]) rs.getArray("refs").getArray()), rs.getString("body"), NOTICE);
			})
			.optional()
			.orElseThrow(() -> new IllegalArgumentException("No unit " + unitId + " in " + act
					+ ": use list_eu_acts for the acts and ids such as art_5, rct_12, anx_III"));
	}

	@Tool(name = "list_eu_acts", description = """
			List the EU acts that can be searched, with their CELEX number, when they entered into force and apply, \
			and the date of the text.""")
	public List<Act> acts() {
		return this.jdbc.sql(ACTS)
			.query((rs, rowNum) -> new Act(rs.getString("celex"), rs.getString("short_name"), rs.getString("title"),
					rs.getString("entry_into_force"), rs.getString("applies_from"), rs.getString("text_version"),
					rs.getString("note"), rs.getLong("units")))
			.list();
	}

	private static Hit hit(Source source) {
		return new Hit(source.document() + ", " + source.label()
				+ (source.heading() == null || source.heading().isBlank() ? "" : " — " + source.heading()),
				source.celex(), source.unitId(), source.url(), source.appliesFrom(), source.applicabilityNote(),
				source.matchedLang(), source.content());
	}

	private static String language(String lang) {
		if (lang == null || lang.isBlank()) {
			return "eng";
		}
		String code = lang.strip().toLowerCase(java.util.Locale.ROOT);
		if (!LANGUAGES.contains(code)) {
			throw new IllegalArgumentException("Unsupported language " + lang + ": use eng, ita, deu, fra or spa");
		}
		return code;
	}

}
