package eu.fontis.ask;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One trace per question for Langfuse: {@code answer-question} with {@code retrieve-context} and the
 * model calls nested under it. Attributes follow Langfuse's OpenTelemetry mapping
 * ({@code langfuse.*}); the question, sources and answer are attached only when
 * {@code fontis.tracing.capture-content} is enabled.
 */
@Component
public class AskTracing {

	/** Tracing switched off: for tests and tools that call the services directly. */
	public static final AskTracing NONE = new AskTracing(ObservationRegistry.NOOP, Tracer.NOOP, false, "test",
			JsonMapper.shared());

	private static final AttributeKey<List<String>> TAGS = AttributeKey.stringArrayKey("langfuse.trace.tags");

	private final ObservationRegistry registry;

	private final Tracer tracer;

	private final boolean captureContent;

	private final String environment;

	private final JsonMapper json;

	@Autowired
	AskTracing(ObservationRegistry registry, Tracer tracer,
			@Value("${fontis.tracing.capture-content}") boolean captureContent,
			@Value("${fontis.tracing.environment}") String environment, JsonMapper json) {
		this.registry = registry;
		this.tracer = tracer;
		this.captureContent = captureContent;
		this.environment = environment;
		this.json = json;
	}

	/**
	 * @param userId internal id of the signed-in user, never the e-mail address; null when anonymous
	 */
	AskResponse answer(String question, String lang, Long userId, Supplier<AskResponse> answer) {
		// A trace of its own rather than a child of the HTTP request span, which is not exported: Langfuse
		// shows the root observation's input and output as the trace's.
		Observation observation = Observation.createNotStarted("fontis.ask", this.registry)
			.contextualName("answer-question")
			.parentObservation(Observation.NOOP);
		try (Tracer.SpanInScope noParent = this.tracer.withSpan(null)) {
			return observation.observe(() -> {
					Span span = Span.current();
					span.setAttribute("langfuse.trace.name", "answer-question");
					span.setAttribute("langfuse.environment", this.environment);
					span.setAttribute(TAGS, List.of(userId == null ? "anonymous" : "signed-in"));
					if (userId != null) {
						span.setAttribute("langfuse.user.id", String.valueOf(userId));
					}
					span.setAttribute("langfuse.observation.type", "chain");
					span.setAttribute("langfuse.observation.metadata.lang", lang);
					if (this.captureContent) {
						span.setAttribute("langfuse.observation.input", question);
					}
					AskResponse response = answer.get();
					span.setAttribute("langfuse.observation.metadata.status", response.status().name());
					span.setAttribute("langfuse.observation.metadata.sources", response.sources().size());
					span.setAttribute("langfuse.observation.metadata.cited", response.citations().size());
					if (response.status() != AskResponse.Status.ANSWERED) {
						span.setAttribute("langfuse.observation.level", "WARNING");
						span.setAttribute("langfuse.observation.status_message", response.status().name());
					}
					if (this.captureContent) {
						span.setAttribute("langfuse.observation.output",
								response.answer() != null ? response.answer() : response.status().name());
					}
					return response;
				});
		}
	}

	Retrieval.Result retrieve(String question, Supplier<Retrieval.Result> retrieve) {
		return Observation.createNotStarted("fontis.retrieve", this.registry)
			.contextualName("retrieve-context")
			.observe(() -> {
				Span span = Span.current();
				span.setAttribute("langfuse.observation.type", "retriever");
				if (this.captureContent) {
					span.setAttribute("langfuse.observation.input", question);
				}
				Retrieval.Result result = retrieve.get();
				span.setAttribute("langfuse.observation.metadata.sources", result.sources().size());
				span.setAttribute("langfuse.observation.metadata.best_similarity", result.bestSimilarity());
				if (this.captureContent) {
					span.setAttribute("langfuse.observation.output", this.json.writeValueAsString(result.sources()
						.stream()
						.map(s -> Map.of("source", s.title(), "celex", s.celex(), "unit", s.unitId(), "similarity",
								s.similarity() == null ? 0.0 : s.similarity()))
						.toList()));
				}
				return result;
			});
	}

}
