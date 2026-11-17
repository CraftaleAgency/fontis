package eu.fontis.ask;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Figures in a text — amounts, percentages, periods, years — compared regardless of how they are
 * written: "EUR 20 000 000", "20.000.000 EUR" and "20 milioni di euro" are the same figure.
 * <p>
 * Used to catch the most harmful wrong answers, a fine or a deadline the cited text does not state.
 * Numbers in references ("Article 83(5)", "Regulation (EU) 2016/679") are not figures. Numbers below
 * 10 without a percent sign are not checked: legislation often writes them in words ("three months").
 */
final class Figures {

	private static final Pattern CITATION_MARKER = Pattern.compile("\\[[\\d\\s,;–-]{1,20}]");

	// "Article 83(5)", "Art. 5", "Annex III", "Erwägungsgrund 28", "paragraph 2", "point (a)"...
	private static final Pattern REFERENCE = Pattern.compile("(?iu)\\b(?:art(?:icles?|icol[oi]|ikel[ns]?|ículos?"
			+ "|s?\\.)|annex(?:es|e)?|allegat[oi]|anh[aä]nge?|anexos?|considerand[oi]|recitals?|erwägungsgr[uü]nde?s?"
			+ "|considérants?|paragraphs?|paragraf[oi]|absatz|abs\\.|comm[ai]|apartados?|par\\.|lettera|points?|punto"
			+ "|chapters?|capo|kapitel|chapitre|capítulo|sections?|sezione|abschnitt|sección)\\s*\\d+[a-z]?"
			+ "(?:\\s*\\(\\s*\\w{1,4}\\s*\\))*");

	// "2016/679", "(EU) 2024/1689": act numbers.
	private static final Pattern ACT_NUMBER = Pattern.compile("\\b\\d{2,4}/\\d{1,4}\\b");

	// A number with thousands separators, or a plain number, with an optional decimal part.
	private static final Pattern NUMBER = Pattern.compile(
			"(?<![\\p{L}\\d])(\\d{1,3}(?:[\\u00a0\\u202f .,'’]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?(?!\\d)");

	private static final Pattern AFTER_NUMBER = Pattern.compile(
			"(?iu)^\\s*(%|per\\s?cent|percent|prozent|pour\\s?cent|por\\s?ciento|"
					+ "mil\\s+millones|millions?|milion[ei]|millionen|mio\\.?|millones|millón|"
					+ "billions?|miliard[oi]|milliarden|milliards?|mrd\\.?)");

	private Figures() {
	}

	/**
	 * @param text as written in the sentence, e.g. "20 milioni"
	 * @param value the number with any "million" or "billion" applied, digits only, e.g. "20000000"
	 * @param percent whether the number is a percentage
	 */
	record Figure(String text, String value, boolean percent) {
	}

	/** Every number in the text, including small ones: what a source can be checked against. */
	static Set<String> values(String text) {
		Set<String> values = new LinkedHashSet<>();
		for (Figure figure : all(text)) {
			values.add(figure.value());
		}
		return values;
	}

	/** The figures in a sentence that are worth checking. */
	static List<Figure> checked(String sentence) {
		return all(sentence).stream().filter(Figures::worthChecking).toList();
	}

	/** Figures of the sentence that are in none of the given texts. */
	static List<String> missing(String sentence, List<String> texts) {
		Set<String> known = new LinkedHashSet<>();
		texts.forEach(t -> known.addAll(values(t)));
		List<String> missing = new ArrayList<>();
		for (Figure figure : checked(sentence)) {
			if (!known.contains(figure.value())) {
				missing.add(figure.text());
			}
		}
		return missing;
	}

	private static boolean worthChecking(Figure figure) {
		if (figure.percent()) {
			return true;
		}
		try {
			return new BigDecimal(figure.value()).compareTo(BigDecimal.TEN) >= 0;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	static List<Figure> all(String text) {
		String clean = CITATION_MARKER.matcher(text).replaceAll(" ");
		clean = REFERENCE.matcher(clean).replaceAll(" ");
		clean = ACT_NUMBER.matcher(clean).replaceAll(" ");
		List<Figure> figures = new ArrayList<>();
		Matcher m = NUMBER.matcher(clean);
		while (m.find()) {
			String integer = m.group(1).replaceAll("\\D", "");
			String decimals = m.group(2);
			Matcher after = AFTER_NUMBER.matcher(clean.substring(m.end()));
			String unit = after.find() ? after.group(1).toLowerCase() : "";
			boolean percent = unit.startsWith("%") || unit.contains("cent") || unit.equals("prozent");
			int zeros = zeros(unit);
			// "2,5 %" and "2.5 %" are the same figure: compare the digits only.
			String value = decimals == null ? stripZeros(integer) : stripZeros(integer) + "." + decimals;
			if (zeros > 0) {
				value = new BigDecimal(decimals == null ? integer : integer + "." + decimals).movePointRight(zeros)
					.stripTrailingZeros()
					.toPlainString();
			}
			String written = clean.substring(m.start(), m.end()) + (unit.isEmpty() ? "" : " " + after.group(1));
			figures.add(new Figure(written.strip(), value, percent));
		}
		return figures;
	}

	/** Zeros that a word after a number adds: 6 for "million", 9 for "billion" ("mil millones"). */
	static int zeros(String unit) {
		if (unit.startsWith("bil") || unit.startsWith("miliard") || unit.startsWith("milliard")
				|| unit.startsWith("mrd") || unit.matches("mil\\s+millones")) {
			return 9;
		}
		if (unit.startsWith("milion") || unit.startsWith("million") || unit.startsWith("millon")
				|| unit.startsWith("millón") || unit.startsWith("mio")) {
			return 6;
		}
		return 0;
	}

	private static String stripZeros(String digits) {
		String stripped = digits.replaceFirst("^0+(?=\\d)", "");
		return stripped.isEmpty() ? "0" : stripped;
	}

}
