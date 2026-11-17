package eu.fontis.ask;

import java.util.List;

import eu.fontis.ask.AnswerSentences.Sentence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerSentencesTests {

	@Test
	void sentencesKeepTheirCitations() {
		List<Sentence> sentences = AnswerSentences.split(
				"Social scoring is prohibited [1]. The fine is up to EUR 35 000 000 [2][3]. See Art. 5 for details.", 3);

		assertThat(sentences).extracting(Sentence::text)
			.containsExactly("Social scoring is prohibited [1].", "The fine is up to EUR 35 000 000 [2][3].",
					"See Art. 5 for details.");
		assertThat(sentences).extracting(Sentence::citations)
			.containsExactly(List.of(1), List.of(2, 3), List.of());
	}

	@Test
	void markersAfterTheFullStopBelongToTheSentenceBefore() {
		assertThat(AnswerSentences.splitLine("It applies from 2 August 2026. [1] It was adopted in 2024. [2]"))
			.containsExactly("It applies from 2 August 2026. [1]", "It was adopted in 2024. [2]");
	}

	@Test
	void decimalsAndAbbreviationsDoNotEndASentence() {
		assertThat(AnswerSentences.splitLine("Up to 2.5 % of turnover, see art. 83 [1]."))
			.containsExactly("Up to 2.5 % of turnover, see art. 83 [1].");
	}

	@Test
	void germanDatesDoNotEndASentence() {
		assertThat(AnswerSentences.splitLine("Sie gelten ab 2. Dezember 2027 [1]. Dies gilt auch für Anhang III [2]."))
			.containsExactly("Sie gelten ab 2. Dezember 2027 [1].", "Dies gilt auch für Anhang III [2].");
	}

	@Test
	void listItemsInheritTheCitationsOfTheirLeadIn() {
		List<Sentence> sentences = AnswerSentences.split("""
				The following practices are prohibited [1]:
				- social scoring;
				- untargeted scraping of facial images [2];
				After the list.""", 2);

		assertThat(sentences).extracting(Sentence::leadIn).containsExactly(true, false, false, false);
		assertThat(sentences).extracting(Sentence::citations)
			.containsExactly(List.of(1), List.of(1), List.of(2), List.of());
	}

}
