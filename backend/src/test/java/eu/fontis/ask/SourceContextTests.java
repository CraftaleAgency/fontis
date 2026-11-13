package eu.fontis.ask;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceContextTests {

	// NIS2 Article 34 in short: the fine for essential entities is in chunk 0, for important ones in chunk 1.
	private static final Map<Integer, String> ARTICLE = Map.of(0, "essential: EUR 10 000 000", 1,
			"important: EUR 7 000 000", 2, "penalty payments");

	@Test
	void allChunksFitTheBudgetAndKeepReadingOrder() {
		// Retrieval found chunk 1 best: the model must still see chunk 0.
		assertThat(SourceContext.assemble(ARTICLE, List.of(1), 1000))
			.isEqualTo("essential: EUR 10 000 000\nimportant: EUR 7 000 000\npenalty payments");
	}

	@Test
	void retrievedChunksComeFirstThenTheLeadInThenNeighboursOfTheBest() {
		// Budget for two chunks: the best (2), then the article's lead-in (0) before the neighbour (1).
		assertThat(SourceContext.assemble(ARTICLE, List.of(2), 45))
			.isEqualTo("essential: EUR 10 000 000" + SourceContext.GAP + "penalty payments");
		Map<Integer, String> longer = Map.of(0, "lead-in", 1, "a", 2, "b", 3, "best");
		assertThat(SourceContext.assemble(longer, List.of(3), 12)).isEqualTo("lead-in" + SourceContext.GAP + "b\nbest");
		// A second retrieved chunk outranks a neighbour: 0 is retrieved, so it is picked before 1.
		assertThat(SourceContext.assemble(ARTICLE, List.of(2, 0), 45))
			.isEqualTo("essential: EUR 10 000 000" + SourceContext.GAP + "penalty payments");
	}

	@Test
	void theBestChunkIsKeptEvenIfLongerThanTheBudget() {
		assertThat(SourceContext.assemble(ARTICLE, List.of(0), 5)).isEqualTo("essential: EUR 10 000 000");
	}

}
