package eu.fontis.ask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import eu.fontis.ask.AskResponse.Status;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerServiceTests {

	private static final AskProperties PROPERTIES = new AskProperties(40, 6, 0.45, 8192, 1024, 2, Duration.ofSeconds(90), 1.0, 1.0, false, List.of(6000, 3000, 3000, 1200));

	private static final List<Source> SOURCES = List.of(
			new Source("32016R0679", "art_17", "GDPR", "Articolo 17", "Diritto alla cancellazione", "url17", "deu",
					"Die betroffene Person hat das Recht ...", 0.03, 0.70, List.of(0)),
			new Source("32016R0679", "rct_65", "GDPR", "(65)", null, "url65", "ita", "Un interessato dovrebbe ...",
					0.02, 0.66, List.of(0)));

	private final List<Prompt> prompts = new ArrayList<>();

	@Test
	void citedAnswerKeepsOnlyCitedSourcesInOrder() {
		AskResponse response = service(0.70, "Sì, puoi chiederne la cancellazione [1]. Vale anche online [2][1].")
			.ask("Posso far cancellare i miei dati?", "ita", Access.EVERYTHING);

		assertThat(response.status()).isEqualTo(Status.ANSWERED);
		assertThat(response.citations()).extracting(AskResponse.Citation::unitId).containsExactly("art_17", "rct_65");
		assertThat(response.sources()).hasSize(2);
		assertThat(response.model()).isEqualTo("test-model");
	}

	@Test
	void promptNumbersSourcesWithTitlesInTheAnswerLanguage() {
		service(0.70, "Sì [1].").ask("Posso far cancellare i miei dati?", "ita", Access.EVERYTHING);

		String user = this.prompts.get(0).getUserMessage().getText();
		assertThat(user).contains("[1] GDPR — Articolo 17 — Diritto alla cancellazione\nDie betroffene Person")
			.contains("[2] GDPR — (65)\n")
			.endsWith("Question: Posso far cancellare i miei dati?");
	}

	@Test
	void lowSimilarityRefusesWithoutCallingTheModel() {
		AskResponse response = service(0.30, "should not be called").ask("Chi ha vinto i mondiali del 2006?", "ita", Access.EVERYTHING);

		assertThat(response.status()).isEqualTo(Status.NO_SOURCE);
		assertThat(response.answer()).isNull();
		assertThat(this.prompts).isEmpty();
	}

	@Test
	void modelRefusalIsReportedAsNoAnswer() {
		AskResponse response = service(0.70, "NO_ANSWER").ask("Quanto costa una licenza?", "ita", Access.EVERYTHING);

		assertThat(response.status()).isEqualTo(Status.NO_ANSWER);
		assertThat(response.citations()).isEmpty();
	}

	@Test
	void answerWithoutCitationsIsFlaggedAsUncited() {
		AskResponse response = service(0.70, "Sì, puoi.").ask("Posso far cancellare i miei dati?", "ita", Access.EVERYTHING);

		assertThat(response.status()).isEqualTo(Status.UNCITED);
	}

	@Test
	void citationListsAndRangesAreRecognised() {
		assertThat(AnswerService.citedNumbers("A [2, 4]. B [1–3]. C [5-6]. D [9]. E [99999999999].", 6))
			.containsExactly(2, 4, 1, 3, 5, 6);
	}

	@Test
	void reasoningBlocksAndOutOfRangeCitationsAreIgnored() {
		AskResponse response = service(0.70, "<think>let me check [2]</think>\nSì [1][9].")
			.ask("Posso far cancellare i miei dati?", "ita", Access.EVERYTHING);

		assertThat(response.answer()).isEqualTo("Sì [1][9].");
		assertThat(response.citations()).extracting(AskResponse.Citation::n).containsExactly(1);
	}

	@Test
	void partialAnswerNamesWhatTheSourcesDoNotSay() {
		AskResponse response = withFeatures(new AnswerFeatures(0, 0, false, true),
				"Sì, puoi chiederne la cancellazione [1].\nNOT_IN_SOURCES: i tempi di risposta del titolare")
			.ask("Posso far cancellare i miei dati e in quanto tempo?", "ita", Access.EVERYTHING);

		assertThat(response.status()).isEqualTo(Status.ANSWERED);
		assertThat(response.answer()).isEqualTo("Sì, puoi chiederne la cancellazione [1].");
		assertThat(response.missing()).isEqualTo("i tempi di risposta del titolare");
		assertThat(this.prompts.get(0).getSystemMessage().getText()).contains("NOT_IN_SOURCES:");
	}

	@Test
	void onlyAMissingLineIsARefusal() {
		AskResponse response = withFeatures(new AnswerFeatures(0, 0, false, true), "NOT_IN_SOURCES: everything")
			.ask("Quanto costa una licenza?", "ita", Access.EVERYTHING);

		assertThat(response.status()).isEqualTo(Status.NO_ANSWER);
		assertThat(response.missing()).isEqualTo("everything");
	}

	@Test
	void applicabilityGivesTodayAndTheDateOfEachSource() {
		withFeatures(new AnswerFeatures(0, 0, true, false), "Sì [1].").ask("Vale già?", "ita", Access.EVERYTHING);

		String user = this.prompts.get(0).getUserMessage().getText();
		assertThat(user).startsWith("Today's date: 2026-11-17\n\nSources:")
			.contains("Diritto alla cancellazione\n(Applies from 2018-05-25.)\nDie betroffene Person");
	}

	@Test
	void withoutFeaturesThePromptIsUnchanged() {
		service(0.70, "Sì [1].").ask("Vale già?", "ita", Access.EVERYTHING);

		assertThat(this.prompts.get(0).getUserMessage().getText()).startsWith("Sources:").doesNotContain("Applies from");
		assertThat(this.prompts.get(0).getSystemMessage().getText()).isEqualTo(AnswerService.SYSTEM_PROMPT);
	}

	private AnswerService withFeatures(AnswerFeatures features, String modelReply) {
		List<Source> dated = List.of(
				new Source("32016R0679", "art_17", "GDPR", "Articolo 17", "Diritto alla cancellazione", "url17", "deu",
						"Die betroffene Person hat das Recht ...", 0.03, 0.70, List.of(0), "2018-05-25", null));
		Retrieval retrieval = (question, lang, limit, mode) -> new Retrieval.Result(dated, 0.70);
		ChatModel model = prompt -> {
			this.prompts.add(prompt);
			return new ChatResponse(List.of(new Generation(new AssistantMessage(modelReply))));
		};
		Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
		return new AnswerService(retrieval, ChatClient.builder(model), PROPERTIES, "test-model", AskTracing.NONE,
				features, null, null, clock);
	}

	private AnswerService service(double bestSimilarity, String modelReply) {
		Retrieval retrieval = (question, lang, limit, mode) -> new Retrieval.Result(SOURCES, bestSimilarity);
		ChatModel model = prompt -> {
			this.prompts.add(prompt);
			return new ChatResponse(List.of(new Generation(new AssistantMessage(modelReply))));
		};
		return new AnswerService(retrieval, ChatClient.builder(model), PROPERTIES, "test-model", AskTracing.NONE);
	}

}
