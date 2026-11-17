package eu.fontis.ask;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional parts of answering, each to be measured on the benchmark before it is switched on by default.
 *
 * @param references units that the best source refers to ("as defined in Article 3", "listed in Annex III")
 * given to the model as extra sources; 0 = none
 * @param definitionChars characters of definitions (from the act's definitions article) of terms used in the
 * sources, given to the model as an extra source; 0 = none
 * @param applicability state in the prompt from when each source applies, and today's date
 * @param partialAnswers let the model answer the part of a question the sources cover and name what they do not
 * cover (a partial refusal) instead of answering everything or nothing
 */
@ConfigurationProperties("fontis.ask.features")
public record AnswerFeatures(int references, int definitionChars, boolean applicability, boolean partialAnswers) {

	/** Everything off: the behaviour measured by benchmark v2. */
	public static final AnswerFeatures NONE = new AnswerFeatures(0, 0, false, false);

}
