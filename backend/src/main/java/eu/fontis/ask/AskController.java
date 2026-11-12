package eu.fontis.ask;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import eu.fontis.documents.DocumentAccess;
import eu.fontis.log.AnswerLog;
import eu.fontis.security.Users;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Validated
class AskController {

	private static final Logger log = LoggerFactory.getLogger(AskController.class);

	static final Set<String> LANGUAGES = Set.of("eng", "ita", "deu", "fra", "spa");

	private final AnswerService answerService;

	private final AnswerLog answerLog;

	private final DocumentAccess documentAccess;

	private final AnswerMetrics metrics;

	private final AskRateLimiter rateLimiter;

	private final Retrieval retrieval;

	private final AskProperties properties;

	private final AskTracing tracing;

	private final Users users;

	// The GPU answers one question at a time: beyond a few, waiting only makes every answer time out.
	private final Semaphore generations;

	AskController(AnswerService answerService, AnswerLog answerLog, DocumentAccess documentAccess,
			AnswerMetrics metrics, AskRateLimiter rateLimiter, Retrieval retrieval, AskProperties properties,
			AskTracing tracing, Users users) {
		this.answerService = answerService;
		this.answerLog = answerLog;
		this.documentAccess = documentAccess;
		this.metrics = metrics;
		this.rateLimiter = rateLimiter;
		this.retrieval = retrieval;
		this.properties = properties;
		this.tracing = tracing;
		this.users = users;
		this.generations = new Semaphore(Math.max(1, properties.maxConcurrent()));
	}

	/**
	 * @param question the question, in any supported language
	 * @param lang ISO 639-2 code for source labels and links (eng, ita, deu, fra, spa); default eng
	 */
	record AskRequest(@NotBlank @Size(max = 1000) String question, String lang) {
	}

	@PostMapping("/ask")
	AskResponse ask(@Valid @RequestBody AskRequest request, Authentication authentication,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		// Behind the proxies, getRemoteAddr() is the client address (server.forward-headers-strategy).
		if (!this.rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
			httpResponse.setHeader("Retry-After", String.valueOf(this.rateLimiter.retryAfterSeconds()));
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many questions, try again shortly");
		}
		String lang = language(request.lang());
		String question = request.question().strip();
		if (!this.generations.tryAcquire()) {
			throw busy(httpResponse, "Fontis is answering other questions, try again shortly");
		}
		AskResponse response;
		try {
			response = this.tracing.answer(question, lang, userId(authentication),
					() -> this.answerService.ask(question, lang, this.documentAccess.of(authentication)));
		}
		catch (RuntimeException ex) {
			// The model server is unreachable, too slow or failed: not something the client did wrong.
			log.warn("Could not answer a question: {}", ex.toString());
			this.metrics.failed(lang);
			throw busy(httpResponse, "The language model is not available, try again shortly");
		}
		finally {
			this.generations.release();
		}
		this.answerLog.record(response, lang);
		this.metrics.record(response, lang);
		return response;
	}

	private static ResponseStatusException busy(HttpServletResponse response, String message) {
		response.setHeader("Retry-After", "10");
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
	}

	/**
	 * Retrieval only, without generation: used by the benchmark to measure retrieval quality and to
	 * compare ranking settings. For administrators only (see SecurityConfig).
	 */
	@GetMapping("/search")
	List<Source> search(@RequestParam @NotBlank @Size(max = 1000) String q,
			@RequestParam(required = false) String lang, @RequestParam(required = false) Integer limit,
			@RequestParam(defaultValue = "HYBRID") Retrieval.Mode mode,
			@RequestParam(required = false) Double textWeight, @RequestParam(required = false) Double recitalWeight,
			@RequestParam(required = false) Boolean sumLanguages, Authentication authentication) {
		int n = limit == null ? this.properties.maxSources() : Math.clamp(limit, 1, 50);
		var options = new Retrieval.Options(mode, textWeight, recitalWeight, sumLanguages,
				this.documentAccess.of(authentication));
		return this.retrieval.search(q.strip(), language(lang), n, options).sources();
	}

	private Long userId(Authentication authentication) {
		if (!DocumentAccess.signedIn(authentication)) {
			return null;
		}
		return this.users.findByEmail(authentication.getName()).map(Users.User::id).orElse(null);
	}

	private static String language(String lang) {
		if (lang == null || lang.isBlank()) {
			return "eng";
		}
		if (!LANGUAGES.contains(lang)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language: " + lang);
		}
		return lang;
	}

}
