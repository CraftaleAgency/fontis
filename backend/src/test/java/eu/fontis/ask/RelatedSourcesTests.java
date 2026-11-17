package eu.fontis.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedSourcesTests {

	private static final String DEFINITIONS = """
			For the purposes of this Regulation, the following definitions apply:
			(1) ‘AI system’ means a machine-based system designed to operate with varying levels of autonomy;
			(3) ‘provider’ means a natural or legal person that develops an AI system;
			(4) ‘deployer’ means a natural or legal person using an AI system under its authority;
			(66) ‘high-risk AI system’ means an AI system referred to in Article 6;""";

	@Test
	void definitionsOfTermsUsedLongestFirstThenInArticleOrder() {
		String used = "the deployer of a high-risk ai system shall inform the persons concerned";

		assertThat(RelatedSources.pick(DEFINITIONS, used, 10_000).lines().toList())
			.extracting(line -> line.substring(0, 4))
			.containsExactly("(1) ", "(4) ", "(66)");
	}

	@Test
	void budgetKeepsTheMostSpecificTerms() {
		String used = "the deployer of a high-risk ai system";

		assertThat(RelatedSources.pick(DEFINITIONS, used, 80)).startsWith("(66) ‘high-risk AI system’");
	}

	@Test
	void otherLanguagesQuotes() {
		String italian = "(3) «fornitore»: una persona fisica o giuridica che sviluppa un sistema di IA;";
		String german = "3. „Anbieter“ eine natürliche oder juristische Person;";

		assertThat(RelatedSources.pick(italian, "il fornitore deve", 1000)).isEqualTo(italian);
		assertThat(RelatedSources.pick(german, "der anbieter muss", 1000)).isEqualTo(german);
	}

	@Test
	void letteredDefinitionsOfTheDigitalServicesAct() {
		String dsa = "(c) ‘consumer’ means any natural person who is acting for purposes outside his or her trade;";

		assertThat(RelatedSources.pick(dsa, "a consumer may complain", 1000)).isEqualTo(dsa);
	}

}
