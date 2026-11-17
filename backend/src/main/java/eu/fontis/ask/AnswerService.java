package eu.fontis.ask;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Answers a question from retrieved sources only, with numbered citations. Refuses rather than
 * guesses: when retrieval finds nothing close enough, or when the model finds the sources
 * insufficient, the response says so instead of answering. After generation, each sentence is checked
 * against the text it cites ({@link AnswerVerifier}).
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

	// Rules added by optional features (AnswerFeatures).
	static final String APPLICABILITY_RULE = """
			- Some sources state from when they apply. If the question is whether a rule already applies, \
			compare that date with today's date and say so.
			""";

	static final String PARTIAL_RULE = """
			- If the sources answer only part of the question, answer that part, then add a last line \
			starting with NOT_IN_SOURCES: followed by what the sources do not say.
			""";

	private static final Pattern MISSING_LINE = Pattern.compile("(?m)^\\s*NOT_IN_SOURCES:\\s*(.*)$");

	private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

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

	private final AnswerFeatures features;

	private final RelatedSources relatedSources;

	private final AnswerVerifier verifier;

	private final Clock clock;

	@Autowired
	AnswerService(Retrieval retrieval, ChatClient.Builder chatClientBuilder, AskProperties properties,
			@Value("${spring.ai.ollama.chat.options.model:unknown}") String modelName, AskTracing tracing,
			AnswerFeatures features, RelatedSources relatedSources, AnswerVerifier verifier) {
		this(retrieval, chatClientBuilder, properties, modelName, tracing, features, relatedSources, verifier,
				Clock.system(BRUSSELS));
	}

	/** Without optional features or verification, as measured by benchmark v2 (and for tests). */
	public AnswerService(Retrieval retrieval, ChatClient.Builder chatClientBuilder, AskProperties properties,
			String modelName, AskTracing tracing) {
		this(retrieval, chatClientBuilder, properties, modelName, tracing, AnswerFeatures.NONE, null, null,
				Clock.system(BRUSSELS));
	}

	AnswerService(Retrieval retrieval, ChatClient.Builder chatClientBuilder, AskProperties properties,
			String modelName, AskTracing tracing, AnswerFeatures features, RelatedSources relatedSources,
			AnswerVerifier verifier, Clock clock) {
		this.retrieval = retrieval;
		this.tracing = tracing;
		this.features = features;
		this.relatedSources = relatedSources;
		this.verifier = verifier;
		this.clock = clock;
		this.chatClient = chatClientBuilder.defaultSystem(systemPrompt(features)).build();
		this.properties = properties;
		this.modelName = modelName;
	}

	static String systemPrompt(AnswerFeatures features) {
		return SYSTEM_PROMPT + (features.applicability() ? APPLICABILITY_RULE : "")
				+ (features.partialAnswers() ? PARTIAL_RULE : "");
	}

	/**
	 * @param access the documents this question may draw on
	 */
	public AskResponse ask(String question, String answerLang, Access access) {
		long start = System.nanoTime();
		Retrieval.Result result = this.tracing.retrieve(question, () -> this.retrieval.search(question, answerLang,
				this.properties.maxSources(), Retrieval.Options.of(access)));
		long retrievalMs = (System.nanoTime() - start) / 1_000_000;

		List<Source> retrieved = result.sources();
		if (retrieved.isEmpty() || result.bestSimilarity() < this.properties.minSimilarity()) {
			List<Citation> numbered = IntStream.range(0, retrieved.size())
				.mapToObj(i -> Citation.of(i + 1, retrieved.get(i)))
				.toList();
			return new AskResponse(question, Status.NO_SOURCE, null, List.of(), numbered, null, retrievalMs, 0);
		}

		// Several chunks per source, not just the best: the answer may be in another part of the article.
		// Better-ranked sources get larger budgets, so the article that answers usually reaches the model whole.
		List<Source> withContext = new ArrayList<>();
		for (int rank = 0; rank < retrieved.size(); rank++) {
			withContext.addAll(this.retrieval.contexts(List.of(retrieved.get(rank)), this.properties.sourceChars(rank)));
		}
		// Units the best source refers to and definitions of the terms used, numbered after the retrieved ones.
		if (this.relatedSources != null && (this.features.references() > 0 || this.features.definitionChars() > 0)) {
			withContext.addAll(this.relatedSources.find(withContext, answerLang, this.features,
					this.properties.sourceChars(retrieved.size())));
		}
		List<Source> sources = List.copyOf(withContext);
		List<Citation> numbered = IntStream.range(0, sources.size())
			.mapToObj(i -> Citation.of(i + 1, sources.get(i)))
			.toList();

		start = System.nanoTime();
		ChatResponse chat = this.chatClient.prompt()
			.options(OllamaChatOptions.builder().disableThinking().numCtx(this.properties.numCtx())
				.numPredict(this.properties.numPredict()))
			.user(userPrompt(question, sources, this.features.applicability() ? LocalDate.now(this.clock) : null))
			.call()
			.chatResponse();
		warnIfContextIsFull(chat);
		String raw = chat == null || chat.getResult() == null ? null : chat.getResult().getOutput().getText();
		long generationMs = (System.nanoTime() - start) / 1_000_000;

		String text = THINK_BLOCK.matcher(raw == null ? "" : raw).replaceAll("").strip();
		String missing = missing(text);
		String answer = MISSING_LINE.matcher(text).replaceAll("").strip();
		if (answer.isEmpty() || answer.startsWith(NO_ANSWER)) {
			return new AskResponse(question, Status.NO_ANSWER, null, List.of(), numbered, this.modelName,
					retrievalMs, generationMs, null, missing);
		}

		List<Citation> cited = citedNumbers(answer, sources.size()).stream().map(n -> numbered.get(n - 1)).toList();
		Status status = cited.isEmpty() ? Status.UNCITED : Status.ANSWERED;
		AskResponse response = new AskResponse(question, status, answer, cited, numbered, this.modelName,
				retrievalMs, generationMs, null, missing);
		return verified(response, sources, answerLang);
	}

	/** What the model said the sources do not cover (partial refusal), or {@code null}. */
	static String missing(String text) {
		Matcher matcher = MISSING_LINE.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		String missing = matcher.group(1).strip();
		return missing.isEmpty() ? null : missing;
	}

	// A failed check must not cost the user the answer: it is returned unchecked.
	private AskResponse verified(AskResponse response, List<Source> sources, String answerLang) {
		if (this.verifier == null || !this.verifier.enabled()) {
			return response;
		}
		try {
			return response.withVerification(
					this.verifier.verify(response.question(), response.answer(), sources, answerLang));
		}
		catch (RuntimeException ex) {
			log.warn("Could not verify an answer: {}", ex.toString());
			return response;
		}
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
		return userPrompt(question, sources, null);
	}

	/**
	 * @param today today's date, given with each source's date of application; {@code null} to give neither
	 */
	static String userPrompt(String question, List<Source> sources, LocalDate today) {
		StringBuilder prompt = new StringBuilder();
		if (today != null) {
			prompt.append("Today's date: ").append(today).append("\n\n");
		}
		prompt.append("Sources:\n\n");
		for (int i = 0; i < sources.size(); i++) {
			Source source = sources.get(i);
			prompt.append('[').append(i + 1).append("] ").append(source.title()).append('\n');
			if (today != null && source.appliesFrom() != null) {
				prompt.append("(Applies from ").append(source.appliesFrom()).append('.');
				if (source.applicabilityNote() != null && !source.applicabilityNote().isBlank()) {
					prompt.append(' ').append(source.applicabilityNote().strip());
				}
				prompt.append(")\n");
			}
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
