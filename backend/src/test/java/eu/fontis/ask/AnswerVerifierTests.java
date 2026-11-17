package eu.fontis.ask;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import eu.fontis.ask.AskResponse.SentenceCheck;
import eu.fontis.ask.AskResponse.Verdict;
import eu.fontis.ask.AskResponse.Verification;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerVerifierTests {

	private static final String ENGLISH = """
			1. The following AI practices shall be prohibited:
			(a) the placing on the market of manipulative AI systems;
			(c) social scoring of natural persons by public authorities;""";

	private static final String ITALIAN = """
			1. Sono vietate le seguenti pratiche di IA:
			(a) l'immissione sul mercato di sistemi di IA manipolativi;
			(c) il punteggio sociale delle persone fisiche da parte delle autorità pubbliche;""";

	private static final Source ARTICLE_5 = new Source("32024R1689", "art_5", "AI Act", "Articolo 5",
			"Pratiche di IA vietate", "url", "eng", ENGLISH, 0.03, 0.7, List.of(0));

	private final AnswerVerifier verifier = new AnswerVerifier(new BagOfWords(),
			(celex, unitId, lang) -> Map.of("eng", ENGLISH, "ita", ITALIAN).get(lang),
			new VerifyProperties(true, 0.5, 0.3, 1500));

	@Test
	void eachSentenceGetsItsPassageFigureCheckAndVerdict() {
		Verification verification = this.verifier.verify("Is social scoring allowed?", """
				Social scoring of natural persons by public authorities is prohibited [1]. \
				The fine is EUR 35 000 000 [1]. Football is popular.""", List.of(ARTICLE_5), "ita");

		List<SentenceCheck> sentences = verification.sentences();
		assertThat(sentences).extracting(SentenceCheck::verdict)
			.containsExactly(Verdict.SUPPORTED, Verdict.FIGURE_MISMATCH, Verdict.UNCITED);
		assertThat(verification.supported()).isEqualTo(1);
		assertThat(verification.total()).isEqualTo(3);

		SentenceCheck first = sentences.get(0);
		assertThat(first.pinpoint()).isEqualTo("Articolo 5(1)(c)");
		assertThat(first.passage()).startsWith("(c) il punteggio sociale");
		assertThat(first.n()).isEqualTo(1);
		assertThat(sentences.get(1).figuresMissing()).containsExactly("35 000 000");
	}

	@Test
	void unrelatedSentenceIsUnsupported() {
		Verification verification = this.verifier.verify("?", "Football is played with a ball [1].",
				List.of(ARTICLE_5), "eng");

		assertThat(verification.sentences()).extracting(SentenceCheck::verdict).containsExactly(Verdict.UNSUPPORTED);
	}

	@Test
	void passageWithTheSentenceFiguresWinsOverASimilarOne() {
		String article83 = """
				4. Infringements shall be subject to fines up to 10 000 000 EUR or up to 2 % of turnover;
				5. Infringements of the principles shall be subject to fines up to 20 000 000 EUR or up to 4 % of turnover;""";
		Source source = new Source("32016R0679", "art_83", "GDPR", "Article 83", null, "url", "eng", article83, 0.03,
				0.7, List.of(0));
		AnswerVerifier verifier = new AnswerVerifier(new BagOfWords(), (celex, unitId, lang) -> article83,
				new VerifyProperties(true, 0.5, 0.3, 1500));

		Verification verification = verifier.verify("?",
				"Infringements shall be subject to fines up to 20 000 000 EUR or up to 4 % of turnover [1].",
				List.of(source), "eng");

		assertThat(verification.sentences().get(0).pinpoint()).isEqualTo("Article 83(5)");
	}

	/** Texts sharing words get similar vectors. */
	static final class BagOfWords implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<String> inputs = request.getInstructions();
			return new EmbeddingResponse(
					IntStream.range(0, inputs.size()).mapToObj(i -> new Embedding(vector(inputs.get(i)), i)).toList());
		}

		@Override
		public float[] embed(Document document) {
			return vector(document.getText());
		}

		private static float[] vector(String text) {
			float[] v = new float[1024];
			for (String word : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
				if (!word.isEmpty()) {
					v[Math.floorMod(word.hashCode(), 1024)] += 1f;
				}
			}
			return v;
		}

	}

}
