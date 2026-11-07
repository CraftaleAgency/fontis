package eu.fontis.ask;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import eu.fontis.ask.AskResponse.Status;
import eu.fontis.tracing.TraceAttributesSpanProcessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The trace exported for a question, with the real OpenTelemetry SDK behind Micrometer. */
class AskTracingTests {

	private final List<SpanData> exported = new CopyOnWriteArrayList<>();

	private final SdkTracerProvider provider = SdkTracerProvider.builder()
		.addSpanProcessor(new TraceAttributesSpanProcessor())
		.addSpanProcessor(SimpleSpanProcessor.create(new CompositeSpanExporter(List.of(new Collector()),
				List.of(TraceAttributesSpanProcessor::isExported), List.of(), List.of())))
		.build();

	private final ObservationRegistry registry = ObservationRegistry.create();

	private final OtelTracer tracer = new OtelTracer(this.provider.get("test"), new OtelCurrentTraceContext(),
			event -> {
			});

	private final AskTracing tracing = new AskTracing(this.registry, this.tracer, true, "test",
			JsonMapper.shared());

	AskTracingTests() {
		this.registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(this.tracer));
	}

	@AfterEach
	void close() {
		this.provider.close();
	}

	@Test
	void aQuestionIsOneTraceWithItsStepsNestedAndNothingElseExported() {
		// The HTTP request span the question arrives in: neither exported nor the trace's parent.
		Observation.createNotStarted("http.server.requests", this.registry).observe(() -> this.tracing
			.answer("What is banned?", "eng", 7L, () -> {
				this.tracing.retrieve("What is banned?", () -> new Retrieval.Result(List.of(), 0.8));
				return new AskResponse("What is banned?", Status.ANSWERED, "Social scoring [1].", List.of(),
						List.of(), "model", 1, 1);
			}));

		Map<String, SpanData> spans = this.exported.stream()
			.collect(Collectors.toMap(SpanData::getName, Function.identity()));
		assertThat(spans).containsOnlyKeys("answer-question", "retrieve-context");

		SpanData root = spans.get("answer-question");
		SpanData retrieval = spans.get("retrieve-context");
		assertThat(root.getParentSpanContext().isValid()).isFalse();
		assertThat(retrieval.getParentSpanId()).isEqualTo(root.getSpanId());
		assertThat(attribute(root, "langfuse.observation.input")).isEqualTo("What is banned?");
		assertThat(attribute(root, "langfuse.observation.output")).isEqualTo("Social scoring [1].");
		assertThat(attribute(root, "langfuse.observation.type")).isEqualTo("chain");
		assertThat(attribute(retrieval, "langfuse.observation.type")).isEqualTo("retriever");
		// Trace attributes reach every span, so Langfuse can filter each observation by them.
		assertThat(attribute(retrieval, "langfuse.trace.name")).isEqualTo("answer-question");
		assertThat(attribute(retrieval, "langfuse.user.id")).isEqualTo("7");
		assertThat(attribute(retrieval, "langfuse.environment")).isEqualTo("test");
		assertThat(retrieval.getAttributes().get(AttributeKey.stringArrayKey("langfuse.trace.tags")))
			.containsExactly("signed-in");
	}

	@Test
	void contentIsNotExportedUnlessEnabled() {
		AskTracing withoutContent = new AskTracing(this.registry, this.tracer, false, "test", JsonMapper.shared());
		withoutContent.answer("What is banned?", "eng", null, () -> new AskResponse("What is banned?",
				Status.NO_SOURCE, null, List.of(), List.of(), null, 1, 0));

		SpanData root = this.exported.getFirst();
		assertThat(root.getAttributes().asMap().keySet()).extracting(AttributeKey::getKey)
			.doesNotContain("langfuse.observation.input", "langfuse.observation.output", "langfuse.user.id")
			.contains("langfuse.observation.level");
	}

	private static String attribute(SpanData span, String key) {
		return span.getAttributes().get(AttributeKey.stringKey(key));
	}

	private final class Collector implements SpanExporter {

		@Override
		public CompletableResultCode export(Collection<SpanData> spans) {
			AskTracingTests.this.exported.addAll(spans);
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode flush() {
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode shutdown() {
			return CompletableResultCode.ofSuccess();
		}

	}

}
