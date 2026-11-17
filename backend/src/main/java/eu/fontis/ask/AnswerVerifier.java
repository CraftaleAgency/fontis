package eu.fontis.ask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.fontis.ask.AskResponse.SentenceCheck;
import eu.fontis.ask.AskResponse.Verdict;
import eu.fontis.ask.AskResponse.Verification;
import eu.fontis.ask.AnswerSentences.Sentence;
import eu.fontis.corpus.Paragraphs;
import eu.fontis.corpus.Paragraphs.Paragraph;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Checks each sentence of an answer against the text it cites, after generation.
 * <p>
 * For every sentence with citations it finds the closest passage (paragraph, point or line) among the
 * text the model was given for the cited sources, by embedding similarity — bge-m3 is multilingual, so a
 * sentence in Italian is compared with a passage found in French. It also checks that every figure in
 * the sentence (amount, percentage, period, year) appears in the cited text. The passage is returned in
 * the answer language with its place in the article ("Articolo 5(1)(a)"), so the user can read exactly
 * what supports the sentence.
 * <p>
 * The similarity thresholds are a first guess, not yet calibrated on the benchmark: the verdicts flag
 * sentences to read with care, they do not change the answer.
 */
@Component
public class AnswerVerifier {

	private static final String GAP_LINE = SourceContext.GAP.strip();

	/** The body of a unit in one language, or {@code null} if the unit does not exist in it. */
	interface UnitBodies {

		String body(String celex, String unitId, String lang);

	}

	private final EmbeddingModel embeddingModel;

	private final UnitBodies bodies;

	private final VerifyProperties properties;

	@Autowired
	AnswerVerifier(EmbeddingModel embeddingModel, JdbcClient jdbc, VerifyProperties properties) {
		this(embeddingModel, (celex, unitId, lang) -> jdbc
			.sql("SELECT body FROM source_unit WHERE celex = :celex AND unit_id = :unitId AND lang = :lang")
			.param("celex", celex)
			.param("unitId", unitId)
			.param("lang", lang)
			.query(String.class)
			.optional()
			.orElse(null), properties);
	}

	AnswerVerifier(EmbeddingModel embeddingModel, UnitBodies bodies, VerifyProperties properties) {
		this.embeddingModel = embeddingModel;
		this.bodies = bodies;
		this.properties = properties;
	}

	public boolean enabled() {
		return this.properties.enabled();
	}

	/**
	 * A passage of a cited source.
	 *
	 * @param n citation number of the source
	 * @param text the passage as given to the model (language of the matched chunk)
	 * @param shown the same passage in the answer language when available
	 * @param pinpoint where it is, e.g. "Articolo 5(1)(a)"
	 */
	record Passage(int n, String text, String shown, String pinpoint) {
	}

	/**
	 * @param question the question, whose own figures a sentence may repeat
	 * @param answer the answer with [n] markers
	 * @param sources the numbered sources as given to the model: source n is at index n - 1
	 * @param answerLang language of the answer, for the passages shown
	 */
	public Verification verify(String question, String answer, List<Source> sources, String answerLang) {
		List<Sentence> sentences = AnswerSentences.split(answer, sources.size()).stream()
			.filter(s -> !s.leadIn())
			.toList();

		Set<Integer> cited = new LinkedHashSet<>();
		sentences.forEach(s -> cited.addAll(s.citations()));
		Map<Integer, List<Passage>> passages = new HashMap<>();
		for (int n : cited) {
			passages.put(n, passages(n, sources.get(n - 1), answerLang));
		}

		// One embedding call: the cited sentences, then every passage.
		List<String> inputs = new ArrayList<>();
		List<Sentence> checked = sentences.stream().filter(s -> !s.citations().isEmpty()).toList();
		checked.forEach(s -> inputs.add(clip(withoutMarkers(s.text()))));
		List<Passage> allPassages = passages.values().stream().flatMap(List::stream).toList();
		allPassages.forEach(p -> inputs.add(clip(p.text())));
		List<float[]> vectors = inputs.isEmpty() || allPassages.isEmpty() ? List.of()
				: this.embeddingModel.embed(inputs);
		Map<Passage, float[]> passageVectors = new HashMap<>();
		for (int i = 0; i < allPassages.size() && !vectors.isEmpty(); i++) {
			passageVectors.put(allPassages.get(i), vectors.get(checked.size() + i));
		}

		List<SentenceCheck> checks = new ArrayList<>();
		int next = 0;
		for (Sentence sentence : sentences) {
			if (sentence.citations().isEmpty()) {
				checks.add(new SentenceCheck(sentence.text(), List.of(), null, null, null, null, List.of(),
						Verdict.UNCITED));
				continue;
			}
			float[] sentenceVector = vectors.isEmpty() ? null : vectors.get(next);
			next++;

			List<String> citedTexts = new ArrayList<>();
			citedTexts.add(question);
			sentence.citations().forEach(n -> citedTexts.add(sources.get(n - 1).content()));
			List<String> figuresMissing = Figures.missing(withoutMarkers(sentence.text()), citedTexts);

			Passage best = null;
			double bestSimilarity = -1;
			for (int n : sentence.citations()) {
				for (Passage passage : passages.getOrDefault(n, List.of())) {
					float[] passageVector = passageVectors.get(passage);
					if (sentenceVector == null || passageVector == null) {
						continue;
					}
					double similarity = cosine(sentenceVector, passageVector);
					if (similarity > bestSimilarity) {
						bestSimilarity = similarity;
						best = passage;
					}
				}
			}
			Double similarity = best == null ? null : Math.round(bestSimilarity * 1000) / 1000.0;
			checks.add(new SentenceCheck(sentence.text(), sentence.citations(), best == null ? null : best.n(),
					best == null ? null : best.pinpoint(), best == null ? null : clip(best.shown()), similarity,
					figuresMissing, verdict(figuresMissing, similarity)));
		}
		return Verification.of(checks);
	}

	private Verdict verdict(List<String> figuresMissing, Double similarity) {
		if (!figuresMissing.isEmpty()) {
			return Verdict.FIGURE_MISMATCH;
		}
		if (similarity == null || similarity < this.properties.weak()) {
			return Verdict.UNSUPPORTED;
		}
		return similarity >= this.properties.supported() ? Verdict.SUPPORTED : Verdict.WEAK;
	}

	/**
	 * The lines of the text given to the model for a source, each with its place in the unit. A line of
	 * the context may be only part of a long paragraph (chunks split long paragraphs into sentences).
	 */
	List<Passage> passages(int n, Source source, String answerLang) {
		String matchedBody = this.bodies.body(source.celex(), source.unitId(), source.matchedLang());
		List<Paragraph> matched = matchedBody == null ? List.of() : Paragraphs.parse(matchedBody);
		Map<List<String>, String> shownByPath = new HashMap<>();
		if (!answerLang.equals(source.matchedLang())) {
			String answerBody = this.bodies.body(source.celex(), source.unitId(), answerLang);
			if (answerBody != null) {
				for (Paragraph paragraph : Paragraphs.parse(answerBody)) {
					if (!paragraph.path().isEmpty()) {
						shownByPath.putIfAbsent(paragraph.path(), paragraph.text());
					}
				}
			}
		}

		List<Passage> passages = new ArrayList<>();
		int from = 0;
		for (String raw : source.content().split("\n")) {
			String line = raw.strip();
			if (line.isEmpty() || line.equals(GAP_LINE)) {
				continue;
			}
			Paragraph paragraph = null;
			// Lines come in reading order, but after a gap the search starts again from the top.
			for (int i = 0; i < matched.size() && paragraph == null; i++) {
				int index = (from + i) % matched.size();
				if (matched.get(index).text().contains(line)) {
					paragraph = matched.get(index);
					from = index;
				}
			}
			// The whole paragraph in the answer language if possible, else the line in the language found.
			String shown = line;
			if (paragraph != null && answerLang.equals(source.matchedLang())) {
				shown = paragraph.text();
			}
			else if (paragraph != null && shownByPath.containsKey(paragraph.path())) {
				shown = shownByPath.get(paragraph.path());
			}
			passages.add(new Passage(n, line, shown, Paragraphs.pinpoint(source.unitId(), source.label(), paragraph)));
		}
		return passages;
	}

	private String clip(String text) {
		int max = this.properties.maxPassageChars();
		return text == null || text.length() <= max ? text : text.substring(0, max) + "…";
	}

	static String withoutMarkers(String sentence) {
		return sentence.replaceAll("\\s*\\[[\\d\\s,;–-]{1,20}]", "").strip();
	}

	static double cosine(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

}
