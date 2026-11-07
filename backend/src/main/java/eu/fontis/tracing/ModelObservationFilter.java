package eu.fontis.tracing;

import java.util.List;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;

/**
 * Prepares Spring AI's model spans for Langfuse: stable names that do not change with the model,
 * explicit observation types and, when content capture is on, the messages sent to the model and
 * its reply as role-labelled messages that Langfuse renders as a conversation. Spring AI does not
 * put content on spans by itself.
 */
final class ModelObservationFilter implements ObservationFilter {

	private final JsonMapper json;

	private final boolean captureContent;

	ModelObservationFilter(JsonMapper json, boolean captureContent) {
		this.json = json;
		this.captureContent = captureContent;
	}

	@Override
	public Observation.Context map(Observation.Context context) {
		if (context instanceof ChatModelObservationContext chat) {
			chat.setContextualName("generate-answer");
			chat.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.type", "generation"));
			if (this.captureContent) {
				addContent(chat);
			}
		}
		else if (context instanceof EmbeddingModelObservationContext embedding) {
			embedding.setContextualName("embed-text");
			embedding.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.type", "embedding"));
			if (this.captureContent) {
				embedding.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input",
						this.json.writeValueAsString(embedding.getRequest().getInstructions())));
			}
		}
		return context;
	}

	private void addContent(ChatModelObservationContext chat) {
		List<Map<String, String>> messages = chat.getRequest()
			.getInstructions()
			.stream()
			.map(m -> Map.of("role", m.getMessageType().getValue(), "content", String.valueOf(m.getText())))
			.toList();
		chat.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", this.json.writeValueAsString(messages)));
		ChatResponse response = chat.getResponse();
		if (response != null && response.getResult() != null) {
			String text = String.valueOf(response.getResult().getOutput().getText());
			chat.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output",
					this.json.writeValueAsString(Map.of("role", "assistant", "content", text))));
		}
	}

}
