package eu.fontis.corpus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a unit body into retrieval chunks. Paragraph lines are packed together up to
 * {@code maxChars}; a single line longer than that is split at sentence boundaries. Lines are
 * never cut mid-sentence, so every chunk reads as a coherent passage of its article.
 */
public final class Chunker {

	private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.;:])\\s+");

	private final int maxChars;

	public Chunker(int maxChars) {
		this.maxChars = maxChars;
	}

	public List<String> split(String body) {
		List<String> pieces = new ArrayList<>();
		for (String line : body.split("\n")) {
			if (line.isBlank()) {
				continue;
			}
			if (line.length() <= this.maxChars) {
				pieces.add(line);
			}
			else {
				pieces.addAll(pack(List.of(SENTENCE_END.split(line)), " "));
			}
		}
		return pack(pieces, "\n");
	}

	private List<String> pack(List<String> parts, String separator) {
		List<String> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String part : parts) {
			if (!current.isEmpty() && current.length() + separator.length() + part.length() > this.maxChars) {
				chunks.add(current.toString());
				current.setLength(0);
			}
			if (!current.isEmpty()) {
				current.append(separator);
			}
			current.append(part);
		}
		if (!current.isEmpty()) {
			chunks.add(current.toString());
		}
		return chunks;
	}

	/** Context line prepended to a chunk before embedding, e.g. "AI Act — Article 5 — Prohibited AI practices". */
	public static String header(String documentName, CorpusUnit unit) {
		StringBuilder header = new StringBuilder(documentName).append(" — ").append(unit.label());
		if (unit.heading() != null && !unit.heading().isBlank()) {
			header.append(" — ").append(unit.heading());
		}
		return header.toString();
	}

}
