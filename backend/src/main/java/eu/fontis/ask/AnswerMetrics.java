package eu.fontis.ask;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

/**
 * Answer metrics for Prometheus: outcomes, and retrieval and generation times as histograms so
 * Grafana can show percentiles. Model calls (tokens, durations) are measured by Spring AI itself.
 */
@Component
class AnswerMetrics {

	private final MeterRegistry registry;

	private final Timer retrieval;

	private final Timer generation;

	AnswerMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.retrieval = Timer.builder("fontis.retrieval")
			.description("Time to retrieve the sources of a question")
			.publishPercentileHistogram()
			.register(registry);
		this.generation = Timer.builder("fontis.generation")
			.description("Time to generate an answer, when the model is called")
			.publishPercentileHistogram()
			.register(registry);
	}

	void record(AskResponse response, String lang) {
		Counter.builder("fontis.answers")
			.description("Questions answered, by outcome and language")
			.tag("status", response.status().name())
			.tag("lang", lang)
			.register(this.registry)
			.increment();
		this.retrieval.record(Duration.ofMillis(response.retrievalMs()));
		if (response.generationMs() > 0) {
			this.generation.record(Duration.ofMillis(response.generationMs()));
		}
	}

}
