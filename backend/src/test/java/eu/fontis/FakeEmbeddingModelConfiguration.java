package eu.fontis;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Deterministic 1024-dimension embeddings, so tests need no model server. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingModelConfiguration {

	@Bean
	@Primary
	EmbeddingModel fakeEmbeddingModel() {
		return new EmbeddingModel() {

			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				List<String> inputs = request.getInstructions();
				return new EmbeddingResponse(
						IntStream.range(0, inputs.size()).mapToObj(i -> new Embedding(vector(inputs.get(i)), i)).toList());
			}

			@Override
			public float[] embed(Document document) {
				return vector(document.getText());
			}

			// Bag of words: texts sharing words get similar vectors, which keeps vector search
			// meaningful (and deterministic) in tests.
			private float[] vector(String text) {
				float[] v = new float[1024];
				for (String word : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
					if (!word.isEmpty()) {
						v[Math.floorMod(word.hashCode(), 1024)] += 1f;
					}
				}
				double norm = 0;
				for (float x : v) {
					norm += x * x;
				}
				if (norm > 0) {
					for (int i = 0; i < v.length; i++) {
						v[i] /= (float) Math.sqrt(norm);
					}
				}
				return v;
			}
		};
	}

}
