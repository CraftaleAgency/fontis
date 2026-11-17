package eu.fontis.corpus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the place of each line of a unit body in the structure of its article: paragraph, point and
 * sub-point, so that a citation can say "Article 5(1)(a)" instead of "Article 5".
 * <p>
 * The corpus keeps one paragraph or point per line, with its marker at the start ("1. ...",
 * "(a) ...", "a) ..." in German and Spanish, "(i) ..."). The markers are the same in every language,
 * so the same line has the same path in every language version of a unit.
 */
public final class Paragraphs {

	private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,3}[a-z]?)\\.\\s");

	private static final Pattern NUMBERED_POINT = Pattern.compile("^\\(?(\\d{1,3}[a-z]?)\\)\\s");

	private static final Pattern LETTER_POINT = Pattern.compile("^\\(?([a-z]{1,3})\\)\\s");

	private static final Pattern ROMAN = Pattern.compile("^[ivxl]+$");

	private Paragraphs() {
	}

	/**
	 * A line of a unit and its place in the unit.
	 *
	 * @param path markers from the outermost, e.g. {@code ["1", "a"]}; empty for text before the first marker
	 * @param text the line
	 */
	public record Paragraph(List<String> path, String text) {

		/** The path as written after an article number, e.g. "(1)(a)"; empty when there is no path. */
		public String suffix() {
			StringBuilder suffix = new StringBuilder();
			for (String segment : this.path) {
				suffix.append('(').append(segment).append(')');
			}
			return suffix.toString();
		}

	}

	public static List<Paragraph> parse(String body) {
		List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
		List<Paragraph> paragraphs = new ArrayList<>();
		String paragraph = null;
		// Definitions are numbered points without a paragraph: Article 3(1), 3(2)... of the AI Act.
		boolean numberedPointsAreParagraphs = false;
		String point = null;
		String subPoint = null;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			Matcher m;
			if ((m = NUMBERED.matcher(line)).find()) {
				paragraph = m.group(1);
				numberedPointsAreParagraphs = false;
				point = null;
				subPoint = null;
			}
			else if ((m = NUMBERED_POINT.matcher(line)).find()) {
				if (paragraph == null || numberedPointsAreParagraphs) {
					paragraph = m.group(1);
					numberedPointsAreParagraphs = true;
					point = null;
				}
				else {
					point = m.group(1);
				}
				subPoint = null;
			}
			else if ((m = LETTER_POINT.matcher(line)).find()) {
				String marker = m.group(1);
				if (point != null && ROMAN.matcher(marker).matches() && isSubPoint(point, marker, lines, i)) {
					subPoint = marker;
				}
				else {
					point = marker;
					subPoint = null;
				}
			}
			List<String> path = new ArrayList<>(3);
			if (paragraph != null) {
				path.add(paragraph);
			}
			if (point != null) {
				path.add(point);
			}
			if (subPoint != null) {
				path.add(subPoint);
			}
			paragraphs.add(new Paragraph(List.copyOf(path), line));
		}
		return paragraphs;
	}

	/**
	 * Whether a roman-looking marker after a point is a sub-point. "(i)" after "(h)" is the next point,
	 * unless the next marker is "(ii)", as in Article 5(1)(h)(i) of the AI Act; after any other point it
	 * is a sub-point.
	 */
	private static boolean isSubPoint(String point, String marker, List<String> lines, int index) {
		if (!follows(point, marker)) {
			return true;
		}
		for (int j = index + 1; j < lines.size(); j++) {
			Matcher next = LETTER_POINT.matcher(lines.get(j));
			if (next.find()) {
				return next.group(1).equals(marker + "i");
			}
			if (NUMBERED.matcher(lines.get(j)).find()) {
				return false;
			}
		}
		return false;
	}

	/** Whether {@code next} is the letter after {@code previous}, e.g. "i" after "h". */
	static boolean follows(String previous, String next) {
		return previous.length() == 1 && next.length() == 1 && next.charAt(0) == previous.charAt(0) + 1;
	}

	/**
	 * Where a passage is, for people: the unit label followed by the path, e.g. "Articolo 5(1)(a)" or
	 * "ANNEX III (5)(b)". Recitals and uploaded documents have no inner structure: just the label.
	 */
	public static String pinpoint(String unitId, String label, Paragraph paragraph) {
		if (paragraph == null || paragraph.path().isEmpty()) {
			return label;
		}
		if (unitId.startsWith("art_")) {
			return label + paragraph.suffix();
		}
		if (unitId.startsWith("anx_")) {
			return label + " " + paragraph.suffix();
		}
		return label;
	}

}
