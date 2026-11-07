package eu.fontis.tracing;

import java.util.List;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * Copies the Langfuse trace attributes (name, user, environment, tags) from a span to every span
 * started under it, including Spring AI's model spans: Langfuse filters and aggregates on each
 * observation, not only on the root.
 */
public final class TraceAttributesSpanProcessor implements SpanProcessor {

	private static final AttributeKey<String> TRACE_NAME = AttributeKey.stringKey("langfuse.trace.name");

	private static final List<AttributeKey<?>> PROPAGATED = List.of(TRACE_NAME,
			AttributeKey.stringKey("langfuse.user.id"), AttributeKey.stringKey("langfuse.environment"),
			AttributeKey.stringArrayKey("langfuse.trace.tags"));

	/**
	 * Whether a span belongs to a traced question. HTTP, security and other framework spans would
	 * clutter Langfuse and count as billable observations.
	 */
	public static boolean isExported(FinishedSpan span) {
		return span.getTags().containsKey(TRACE_NAME.getKey());
	}

	@Override
	public void onStart(Context parentContext, ReadWriteSpan span) {
		if (Span.fromContext(parentContext) instanceof ReadableSpan parent) {
			PROPAGATED.forEach(key -> copy(key, parent, span));
		}
	}

	private static <T> void copy(AttributeKey<T> key, ReadableSpan from, ReadWriteSpan to) {
		T value = from.getAttribute(key);
		if (value != null) {
			to.setAttribute(key, value);
		}
	}

	@Override
	public boolean isStartRequired() {
		return true;
	}

	@Override
	public void onEnd(ReadableSpan span) {
	}

	@Override
	public boolean isEndRequired() {
		return false;
	}

}
