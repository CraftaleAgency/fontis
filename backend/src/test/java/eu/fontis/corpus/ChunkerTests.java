package eu.fontis.corpus;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTests {

	private final Chunker chunker = new Chunker(100);

	@Test
	void shortBodyIsOneChunk() {
		assertThat(this.chunker.split("1. First paragraph.\n2. Second paragraph."))
			.containsExactly("1. First paragraph.\n2. Second paragraph.");
	}

	@Test
	void paragraphsArePackedUpToTheLimitWithoutBeingCut() {
		String a = "a".repeat(60);
		String b = "b".repeat(60);
		String c = "c".repeat(30);
		assertThat(this.chunker.split(a + "\n" + b + "\n" + c)).containsExactly(a, b + "\n" + c);
	}

	@Test
	void aParagraphLongerThanTheLimitIsSplitAtSentenceBoundaries() {
		String sentence = "This sentence has exactly forty-two chars.";
		List<String> chunks = this.chunker.split(String.join(" ", sentence, sentence, sentence, sentence));
		assertThat(chunks).hasSize(2).allSatisfy(chunk -> assertThat(chunk).endsWith("."));
		assertThat(String.join(" ", chunks)).isEqualTo(String.join(" ", sentence, sentence, sentence, sentence));
	}

	@Test
	void blankLinesAreIgnored() {
		assertThat(this.chunker.split("one\n\n  \ntwo")).containsExactly("one\ntwo");
	}

	@Test
	void headerNamesDocumentArticleAndTitle() {
		CorpusUnit article = new CorpusUnit("32024R1689", "eng", "art_5", "article", "Article 5",
				"Prohibited AI practices", "body", "url");
		CorpusUnit recital = new CorpusUnit("32024R1689", "eng", "rct_1", "recital", "(1)", null, "body", "url");
		assertThat(Chunker.header("AI Act", article)).isEqualTo("AI Act — Article 5 — Prohibited AI practices");
		assertThat(Chunker.header("AI Act", recital)).isEqualTo("AI Act — (1)");
	}

}
