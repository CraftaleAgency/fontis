package eu.fontis.ask;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the check of each answer sentence against the cited text ({@link AnswerVerifier}).
 *
 * @param enabled whether answers are checked (one extra embedding call per answer)
 * @param supported similarity from which the best cited passage counts as supporting the sentence
 * @param weak similarity from which the best cited passage counts as related; below it the sentence is unsupported
 * @param maxPassageChars longest passage embedded and returned; longer lines are cut
 */
@ConfigurationProperties("fontis.verify")
public record VerifyProperties(boolean enabled, double supported, double weak, int maxPassageChars) {
}
