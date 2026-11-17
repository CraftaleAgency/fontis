package eu.fontis.corpus;

import java.util.List;

import eu.fontis.corpus.Paragraphs.Paragraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParagraphsTests {

	@Test
	void paragraphsPointsAndSubPoints() {
		List<Paragraph> paragraphs = Paragraphs.parse("""
				1. The following AI practices shall be prohibited:
				(a) the placing on the market of manipulative AI systems;
				(h) the use of 'real-time' remote biometric identification systems, unless:
				(i) the targeted search for specific victims;
				(ii) the prevention of a threat;
				2. The use of such systems shall be authorised.""");

		assertThat(paragraphs).extracting(Paragraph::suffix)
			.containsExactly("(1)", "(1)(a)", "(1)(h)", "(1)(h)(i)", "(1)(h)(ii)", "(2)");
	}

	@Test
	void letterIAfterHIsAPoint() {
		List<Paragraph> paragraphs = Paragraphs.parse("""
				1. Lead-in:
				(h) eighth;
				(i) ninth;
				(j) tenth.""");

		assertThat(paragraphs).extracting(Paragraph::suffix).containsExactly("(1)", "(1)(h)", "(1)(i)", "(1)(j)");
	}

	@Test
	void germanAndSpanishPointsWithoutOpeningBracket() {
		List<Paragraph> paragraphs = Paragraphs.parse("""
				1. Folgende Praktiken im KI-Bereich sind verboten:
				a) das Inverkehrbringen eines KI-Systems;
				b) das Inverkehrbringen eines anderen KI-Systems;""");

		assertThat(paragraphs).extracting(Paragraph::suffix).containsExactly("(1)", "(1)(a)", "(1)(b)");
	}

	@Test
	void numberedDefinitionsWithoutParagraph() {
		List<Paragraph> paragraphs = Paragraphs.parse("""
				For the purposes of this Regulation, the following definitions apply:
				(1) ‘AI system’ means a machine-based system;
				(3) ‘provider’ means a natural or legal person;""");

		assertThat(paragraphs).extracting(Paragraph::suffix).containsExactly("", "(1)", "(3)");
	}

	@Test
	void pinpointByUnitType() {
		Paragraph point = new Paragraph(List.of("5", "a"), "(a) text");
		assertThat(Paragraphs.pinpoint("art_83", "Articolo 83", point)).isEqualTo("Articolo 83(5)(a)");
		assertThat(Paragraphs.pinpoint("anx_III", "ANNEX III", point)).isEqualTo("ANNEX III (5)(a)");
		assertThat(Paragraphs.pinpoint("rct_28", "(28)", point)).isEqualTo("(28)");
		assertThat(Paragraphs.pinpoint("art_5", "Article 5", null)).isEqualTo("Article 5");
	}

}
