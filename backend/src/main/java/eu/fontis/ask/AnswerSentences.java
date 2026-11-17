package eu.fontis.ask;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an answer into the sentences that state something, with the sources each one cites.
 * <p>
 * A sentence ends at a full stop, question or exclamation mark followed by an upper-case letter or the
 * end of the line, so "Art. 5" and "2.5 %" stay in one sentence; citation markers right after the end
 * belong to the sentence before. Each line of a list is a sentence. A list item without citations
 * takes those of its lead-in ("The following practices are prohibited [1]:").
 */
final class AnswerSentences {

	private static final Pattern CITATIONS_AHEAD = Pattern.compile("\\s*\\[[\\d\\s,;–-]{1,20}]");

	private static final Pattern LIST_ITEM = Pattern.compile("^(?:[-*•–]|\\d{1,2}[.)]|\\(?[a-z]{1,3}\\))\\s+");

	private static final Pattern LETTER = Pattern.compile("\\p{L}");

	private AnswerSentences() {
	}

	/**
	 * @param text the sentence as written, with its citation markers
	 * @param citations citation numbers, in order of appearance, possibly inherited from a lead-in
	 * @param leadIn whether the sentence introduces a list (ends with a colon); it is not checked itself
	 */
	record Sentence(String text, List<Integer> citations, boolean leadIn) {
	}

	static List<Sentence> split(String answer, int sourceCount) {
		List<Sentence> sentences = new ArrayList<>();
		List<Integer> leadInCitations = List.of();
		for (String raw : answer.split("\n")) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			boolean listItem = LIST_ITEM.matcher(line).find();
			if (!listItem) {
				leadInCitations = List.of();
			}
			for (String text : splitLine(line)) {
				if (!LETTER.matcher(text).find()) {
					continue;
				}
				List<Integer> citations = AnswerService.citedNumbers(text, sourceCount);
				boolean leadIn = text.replaceAll("\\[[^\\]]*]", "").strip().endsWith(":");
				if (citations.isEmpty() && listItem) {
					citations = leadInCitations;
				}
				if (leadIn) {
					leadInCitations = citations;
				}
				sentences.add(new Sentence(text, citations, leadIn));
			}
		}
		return sentences;
	}

	static List<String> splitLine(String line) {
		List<String> parts = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c != '.' && c != '!' && c != '?') {
				continue;
			}
			// German ordinals and dates: "ab 2. Dezember 2027", "der 3. Absatz".
			if (c == '.' && afterShortNumber(line, i)) {
				continue;
			}
			int end = i + 1;
			Matcher markers = CITATIONS_AHEAD.matcher(line);
			markers.region(end, line.length());
			while (markers.lookingAt()) {
				end = markers.end();
				markers.region(end, line.length());
			}
			int next = end;
			while (next < line.length() && Character.isWhitespace(line.charAt(next))) {
				next++;
			}
			boolean atEnd = next == line.length();
			boolean newSentence = next > end && next < line.length() && startsSentence(line.charAt(next));
			if (atEnd || newSentence) {
				parts.add(line.substring(start, end).strip());
				start = next;
				i = Math.max(i, next - 1);
			}
		}
		if (start < line.length()) {
			String rest = line.substring(start).strip();
			if (!rest.isEmpty()) {
				parts.add(rest);
			}
		}
		return parts;
	}

	/** Whether the full stop at {@code dot} ends a number of one or two digits standing alone. */
	static boolean afterShortNumber(String line, int dot) {
		int start = dot;
		while (start > 0 && Character.isDigit(line.charAt(start - 1))) {
			start--;
		}
		int digits = dot - start;
		return digits >= 1 && digits <= 2 && (start == 0 || Character.isWhitespace(line.charAt(start - 1)));
	}

	private static boolean startsSentence(char c) {
		return Character.isUpperCase(c) || c == '«' || c == '"' || c == '„' || c == '“' || c == '¿' || c == '¡';
	}

}
