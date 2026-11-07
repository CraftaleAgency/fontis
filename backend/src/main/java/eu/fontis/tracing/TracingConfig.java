package eu.fontis.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import io.opentelemetry.sdk.trace.SpanProcessor;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;

/**
 * Traces for Langfuse (or any OpenTelemetry backend): one trace per question, with retrieval and
 * model calls nested under it. See docs/observability.md.
 */
@Configuration(proxyBeanMethods = false)
class TracingConfig {

	@Bean
	SpanProcessor traceAttributesSpanProcessor() {
		return new TraceAttributesSpanProcessor();
	}

	@Bean
	SpanExportingPredicate onlyQuestionSpans() {
		return TraceAttributesSpanProcessor::isExported;
	}

	/**
	 * Skips Spring AI's chat client and advisor observations, which only wrap the model call, and the
	 * HTTP calls to the model server, which repeat it: the model call then sits directly under the
	 * step that made it. Their metrics are not used; the model call keeps its own.
	 */
	@Bean
	ObservationPredicate noModelCallWrappers(ObjectProvider<ObservationRegistry> registry) {
		return (name, context) -> {
			if (context instanceof ChatClientObservationContext || context instanceof AdvisorObservationContext) {
				return false;
			}
			if (context instanceof ClientRequestObservationContext) {
				Observation current = registry.getObject().getCurrentObservation();
				return current == null || !(current.getContext() instanceof ModelObservationContext<?, ?>);
			}
			return true;
		};
	}

	/** Questions, sources and answers leave the server only when capture-content is enabled. */
	@Bean
	ModelObservationFilter modelObservationFilter(JsonMapper json,
			@Value("${fontis.tracing.capture-content}") boolean captureContent) {
		return new ModelObservationFilter(json, captureContent);
	}

}
