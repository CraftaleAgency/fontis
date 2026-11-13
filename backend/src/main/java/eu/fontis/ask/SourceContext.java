package eu.fontis.ask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the text of a source given to the model from several chunks of its unit, not just the best
 * one. A long article is split into chunks, and the chunk that matches the question best is not
 * always the one that answers it (NIS2 Article 34 states the fines for essential and for important
 * entities in different chunks).
 * <p>
 * Chunks are picked in this order until the character budget is used: the retrieved chunks, best
 * first, then the article's first chunk (its lead-in, e.g. "The following AI practices shall be
 * prohibited:"), then the others by distance from the best one. They are then put back in reading order,
 * with a gap marker where chunks were skipped.
 */
final class SourceContext {

	static final String GAP = "\n[…]\n";

	private SourceContext() {
	}

	/**
	 * @param chunks every chunk of the unit in one language, by chunk index
	 * @param retrieved indexes of the chunks found by retrieval, best first (never empty)
	 * @param budget maximum characters; the best chunk is always included even if longer
	 */
	static String assemble(Map<Integer, String> chunks, List<Integer> retrieved, int budget) {
		int best = retrieved.get(0);
		Set<Integer> order = new LinkedHashSet<>(retrieved);
		chunks.keySet().stream().min(Integer::compare).ifPresent(order::add);
		chunks.keySet()
			.stream()
			.sorted(Comparator.comparingInt((Integer i) -> Math.abs(i - best)).thenComparingInt(i -> i))
			.forEach(order::add);

		TreeMap<Integer, String> picked = new TreeMap<>();
		int used = 0;
		for (int index : order) {
			String text = chunks.get(index);
			if (text == null) {
				continue;
			}
			if (!picked.isEmpty() && used + text.length() > budget) {
				continue;
			}
			picked.put(index, text);
			used += text.length();
		}

		StringBuilder context = new StringBuilder();
		Integer previous = null;
		for (Map.Entry<Integer, String> entry : picked.entrySet()) {
			if (previous != null) {
				context.append(entry.getKey() == previous + 1 ? "\n" : GAP);
			}
			context.append(entry.getValue());
			previous = entry.getKey();
		}
		return context.toString();
	}

	static List<Integer> parseIndexes(Object sqlArray) throws java.sql.SQLException {
		List<Integer> indexes = new ArrayList<>();
		for (Object value : (Object[]) ((java.sql.Array) sqlArray).getArray()) {
			indexes.add(((Number) value).intValue());
		}
		return indexes;
	}

}
