package eu.fontis.ask;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FiguresTests {

	private static final String ARTICLE_83 = """
			5. Infringements of the following provisions shall be subject to administrative fines up to \
			20 000 000 EUR, or in the case of an undertaking, up to 4 % of the total worldwide annual turnover.""";

	@Test
	void sameFigureWrittenDifferently() {
		assertThat(Figures.missing("La sanzione arriva a 20 milioni di euro o al 4 % del fatturato [1].",
				List.of(ARTICLE_83)))
			.isEmpty();
		assertThat(Figures.missing("Up to EUR 20.000.000 or 4% [1].", List.of(ARTICLE_83))).isEmpty();
	}

	@Test
	void figureNotInTheSourceIsReported() {
		assertThat(Figures.missing("The fine is up to EUR 10 000 000 or 2 % of turnover [1].", List.of(ARTICLE_83)))
			.containsExactly("10 000 000", "2 %");
	}

	@Test
	void referencesAndSmallNumbersAreNotFigures() {
		assertThat(Figures.checked("Under Article 83(5) of Regulation (EU) 2016/679, within 3 months [1]."))
			.isEmpty();
		assertThat(Figures.checked("Notify within 72 hours, from 25 May 2018 [1].")).extracting(Figures.Figure::value)
			.containsExactly("72", "25", "2018");
	}

	@Test
	void billionsAndDecimals() {
		assertThat(Figures.all("2,5 miliardi")).extracting(Figures.Figure::value).containsExactly("2500000000");
		assertThat(Figures.all("1.5 million")).extracting(Figures.Figure::value).containsExactly("1500000");
		assertThat(Figures.zeros("mil millones")).isEqualTo(9);
		assertThat(Figures.zeros("millones")).isEqualTo(6);
	}

	@Test
	void questionFiguresCount() {
		assertThat(Figures.missing("Yes, a company with 250 employees must comply [1].",
				List.of("Does a company with 250 employees have to comply?", "no figures here")))
			.isEmpty();
	}

}
