package eu.fontis.ask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import eu.fontis.ask.AskResponse.Citation;
import eu.fontis.ask.AskResponse.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Answers a question from retrieved sources only, with numbered citations. Refuses rather than
 * guesses: when retrieval finds nothing close enough, or when the model finds the sources
 * insufficient, the response says so instead of answering.
 */
@Service
public class AnswerService {

	private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

	static final String NO_ANSWER = "NO_ANSWER";

	static final String SYSTEM_PROMPT = """
			You are Fontis, an assistant that answers questions about EU legislation and the organisation's \
			documents using ONLY the numbered sources provided by the user.

			Rules:
			- Answer in the same language as the question.
			- End every sentence that states a fact with the number of its source in square brackets, \
			for example [1] or [2][3].
			- Use only information contained in the sources. Do not use prior knowledge.
			- If the sources do not contain the answer, reply with exactly: NO_ANSWER
			- Be concise: at most six sentences.
			""";

	// [1], and also lists and ranges some models write: [1, 3], [1-3], [1–3].
	private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3}(?:\\s*[,;–-]\\s*\\d{1,3})*)]");

	private static final Pattern NUMBER_OR_RANGE = Pattern.compile("(\\d{1,3})(?:\\s*[–-]\\s*(\\d{1,3}))?");

	// Some models still emit a reasoning block even with thinking disabled.
	private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

	private final Retrieval retrieval;

	private final ChatClient chatClient;

	private final AskProperties properties;

	private final String modelName;

	private final AskTracing tracing;

	public AnswerService(Retrieval retrieval, ChatClient.Builder chatClientBuilder, AskProperties properties,
			@Value("${spring.ai.ollama.chat.options.model:unknown}") String modelName, AskTracing tracing) {
		this.retrieval = retrieval;
		this.tracing = tracing;
		this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
		this.properties = properties;
		this.modelName = modelName;
	}

	/**
	 * @param access the documents this question may draw on
	 */
	public AskResponse ask(String question, String answerLang, Access access) {
		long start = System.nanoTime();
		Retrieval.Result result = this.tracing.retrieve(question, () -> this.retrieval.search(question, answerLang,
				this.properties.maxSources(), Retrieval.Options.of(access)));
		long retrievalMs = (System.nanoTime() - start) / 1_000_000;

		List<Source> sources = result.sources();
		List<Citation> numbered = IntStream.range(0, sources.size())
			.mapToObj(i -> Citation.of(i + 1, sources.get(i)))
			.toList();

		if (sources.isEmpty() || result.bestSimilarity() < this.properties.minSimilarity()) {
			return new AskResponse(question, Status.NO_SOURCE, null, List.of(), numbered, null, retrievalMs, 0);
		}

		// Several chunks per source, not just the best: the answer may be in another part of the article.
		// Better-ranked sources get larger budgets, so the article that answers usually reaches the model whole.
		List<Source> withContext = new ArrayList<>();
		for (int rank = 0; rank < sources.size(); rank++) {
			withContext.addAll(this.retrieval.contexts(List.of(sources.get(rank)), this.properties.sourceChars(rank)));
		}

		start = System.nanoTime();
		ChatResponse chat = this.chatClient.prompt()
			.options(OllamaChatOptions.builder().disableThinking().numCtx(this.properties.numCtx())
				.numPredict(this.properties.numPredict()))
			.user(userPrompt(question, withContext))
			.call()
			.chatResponse();
		warnIfContextIsFull(chat);
		String raw = chat == null || chat.getResult() == null ? null : chat.getResult().getOutput().getText();
		long generationMs = (System.nanoTime() - start) / 1_000_000;

		String answer = THINK_BLOCK.matcher(raw == null ? "" : raw).replaceAll("").strip();
		if (answer.isEmpty() || answer.startsWith(NO_ANSWER)) {
			return new AskResponse(question, Status.NO_ANSWER, null, List.of(), numbered, this.modelName,
					retrievalMs, generationMs);
		}

		List<Citation> cited = citedNumbers(answer, sources.size()).stream().map(n -> numbered.get(n - 1)).toList();
		Status status = cited.isEmpty() ? Status.UNCITED : Status.ANSWERED;
		return new AskResponse(question, status, answer, cited, numbered, this.modelName, retrievalMs,
				generationMs);
	}

	// Ollama silently drops the start of a prompt longer than the context window: the sources.
	private void warnIfContextIsFull(ChatResponse chat) {
		if (chat == null || chat.getMetadata() == null || chat.getMetadata().getUsage() == null) {
			return;
		}
		Integer prompt = chat.getMetadata().getUsage().getPromptTokens();
		if (prompt != null && prompt > this.properties.numCtx() - this.properties.numPredict()) {
			log.warn("Prompt of {} tokens leaves less than num-predict in a context of {}: sources may be cut", prompt,
					this.properties.numCtx());
		}
	}

	static String userPrompt(String question, List<Source> sources) {
		StringBuilder prompt = new StringBuilder("Sources:\n\n");
		for (int i = 0; i < sources.size(); i++) {
			Source source = sources.get(i);
			prompt.append('[').append(i + 1).append("] ").append(source.title()).append('\n');
			prompt.append(source.content()).append("\n\n");
		}
		return prompt.append("Question: ").append(question).toString();
	}

	/** Citation numbers in order of first appearance, ignoring numbers with no matching source. */
	static List<Integer> citedNumbers(String answer, int sourceCount) {
		Set<Integer> numbers = new LinkedHashSet<>();
		Matcher matcher = CITATION.matcher(answer);
		while (matcher.find()) {
			Matcher part = NUMBER_OR_RANGE.matcher(matcher.group(1));
			while (part.find()) {
				int from = Integer.parseInt(part.group(1));
				int to = part.group(2) == null ? from : Integer.parseInt(part.group(2));
				for (int n = Math.max(1, from); n <= Math.min(to, sourceCount); n++) {
					numbers.add(n);
				}
			}
		}
		return new ArrayList<>(numbers);
	}

}
