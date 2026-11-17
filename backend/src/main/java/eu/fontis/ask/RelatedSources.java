package eu.fontis.ask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sources that retrieval does not find but an answer may need: the units the best source refers to
 * ("the high-risk AI systems listed in Annex III") and the definitions of the terms the sources use
 * (Article 3 of the AI Act defines 68 terms, among them "provider" and "deployer").
 * <p>
 * References are explicit in the text and extracted when the corpus is built ({@code source_unit.refs});
 * definitions are found by their quoted term at the start of each point of the definitions article
 * ("(3) ‘provider’ means ..."), in every language.
 */
@Component
class RelatedSources {

	// "(3) ‘provider’ means", "(3) «fornitore»:", "3. „Anbieter“", "3) «proveedor»:", and lettered points as in
	// the Digital Services Act: "(a) ‘information society service’", "a) «servizio della società...»".
	private static final Pattern DEFINITION = Pattern.compile(
			"^\\(?(?:\\d{1,3}|[a-z]{1,2})[.)]?\\s*[‘'\"«„“‚]\\s*([^’'\"»“”‘‚]{3,80}?)\\s*[’'\"»“”‘]");

	private static final String UNIT = """
			SELECT u.celex, u.unit_id, d.short_name, u.lang AS matched_lang, u.body,
			       coalesce(a.label, u.label) AS label, coalesce(a.heading, u.heading) AS heading,
			       coalesce(a.url, u.url) AS url,
			       coalesce(u.applies_from, d.applies_from)::text AS applies_from, u.applicability_note
			FROM source_unit u
			JOIN source_document d USING (celex)
			LEFT JOIN source_unit a ON a.celex = u.celex AND a.unit_id = u.unit_id AND a.lang = :answerLang
			WHERE u.celex = :celex AND u.unit_id = :unitId AND u.lang = :lang
			""";

	private final JdbcClient jdbc;

	RelatedSources(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param sources the retrieved sources with their context, best first
	 * @param charsPerReference text given for each referenced unit
	 * @return sources to add after the retrieved ones, at most {@code features.references()} referenced units and
	 * one source of definitions
	 */
	List<Source> find(List<Source> sources, String answerLang, AnswerFeatures features, int charsPerReference) {
		if (sources.isEmpty()) {
			return List.of();
		}
		Set<String> present = new HashSet<>();
		sources.forEach(s -> present.add(s.celex() + "|" + s.unitId()));
		List<Source> related = new ArrayList<>();
		Source best = sources.get(0);

		if (features.references() > 0) {
			List<String> refs = this.jdbc
				.sql("SELECT refs FROM source_unit WHERE celex = :celex AND unit_id = :unitId AND lang = :lang")
				.param("celex", best.celex())
				.param("unitId", best.unitId())
				.param("lang", best.matchedLang())
				.query((rs, rowNum) -> List.of((String[]) rs.getArray("refs").getArray()))
				.optional()
				.orElse(List.of());
			for (String ref : refs) {
				if (related.size() >= features.references()) {
					break;
				}
				if (present.add(best.celex() + "|" + ref)) {
					unit(best.celex(), ref, best.matchedLang(), answerLang)
						.map(s -> s.withContent(clip(s.content(), charsPerReference)))
						.ifPresent(related::add);
				}
			}
		}

		if (features.definitionChars() > 0) {
			definitions(sources, answerLang, features.definitionChars(), present).ifPresent(related::add);
		}
		return related;
	}

	/** The definitions of the best source's act whose terms appear in the sources in the same language. */
	private java.util.Optional<Source> definitions(List<Source> sources, String answerLang, int budget,
			Set<String> present) {
		Source best = sources.get(0);
		String definitionsUnit = this.jdbc
			.sql("SELECT definitions_unit FROM source_document WHERE celex = :celex")
			.param("celex", best.celex())
			.query(String.class)
			.optional()
			.orElse(null);
		if (definitionsUnit == null || present.contains(best.celex() + "|" + definitionsUnit)) {
			return java.util.Optional.empty();
		}
		String lang = best.matchedLang();
		StringBuilder text = new StringBuilder();
		sources.stream()
			.filter(s -> s.celex().equals(best.celex()) && s.matchedLang().equals(lang))
			.forEach(s -> text.append(s.content().toLowerCase(Locale.ROOT)).append('\n'));
		String used = text.toString();

		return unit(best.celex(), definitionsUnit, lang, answerLang).flatMap(unit -> {
			String picked = pick(unit.content(), used, budget);
			return picked.isEmpty() ? java.util.Optional.empty()
					: java.util.Optional.of(unit.withContent(picked));
		});
	}

	/**
	 * Definition lines whose term occurs in the text, longest terms first ("high-risk AI system" before
	 * "AI system"), within the budget, then put back in article order.
	 */
	static String pick(String definitionsBody, String usedText, int budget) {
		record Definition(int order, String term, String line) {
		}
		List<Definition> found = new ArrayList<>();
		String[] lines = definitionsBody.split("\n");
		for (int i = 0; i < lines.length; i++) {
			Matcher m = DEFINITION.matcher(lines[i].strip());
			if (m.find()) {
				String term = m.group(1).strip().toLowerCase(Locale.ROOT);
				if (usedText.contains(term)) {
					found.add(new Definition(i, term, lines[i].strip()));
				}
			}
		}
		found.sort(Comparator.comparingInt((Definition d) -> d.term().length()).reversed());
		List<Definition> kept = new ArrayList<>();
		int used = 0;
		for (Definition definition : found) {
			if (used + definition.line().length() > budget) {
				continue;
			}
			kept.add(definition);
			used += definition.line().length() + 1;
		}
		kept.sort(Comparator.comparingInt(Definition::order));
		return String.join("\n", kept.stream().map(Definition::line).toList());
	}

	private java.util.Optional<Source> unit(String celex, String unitId, String lang, String answerLang) {
		return this.jdbc.sql(UNIT)
			.param("celex", celex)
			.param("unitId", unitId)
			.param("lang", lang)
			.param("answerLang", answerLang)
			.query((rs, rowNum) -> new Source(rs.getString("celex"), rs.getString("unit_id"),
					rs.getString("short_name"), rs.getString("label"), rs.getString("heading"), rs.getString("url"),
					rs.getString("matched_lang"), rs.getString("body"), 0, null, List.of(0),
					rs.getString("applies_from"), rs.getString("applicability_note")))
			.optional();
	}

	static String clip(String text, int max) {
		if (text.length() <= max) {
			return text;
		}
		int cut = text.lastIndexOf('\n', max);
		return (cut > max / 2 ? text.substring(0, cut) : text.substring(0, max)) + SourceContext.GAP.stripTrailing();
	}

}
