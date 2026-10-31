package eu.fontis.ask;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
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

	static final Set<String> LANGUAGES = Set.of("eng", "ita", "deu", "fra", "spa");

	private final AnswerService answerService;

	private final Retrieval retrieval;

	private final AskProperties properties;

	AskController(AnswerService answerService, Retrieval retrieval, AskProperties properties) {
		this.answerService = answerService;
		this.retrieval = retrieval;
		this.properties = properties;
	}

	/**
	 * @param question the question, in any supported language
	 * @param lang ISO 639-2 code for source labels and links (eng, ita, deu, fra, spa); default eng
	 */
	record AskRequest(@NotBlank @Size(max = 1000) String question, String lang) {
	}

	@PostMapping("/ask")
	AskResponse ask(@Valid @RequestBody AskRequest request) {
		return this.answerService.ask(request.question().strip(), language(request.lang()));
	}

	/** Retrieval only, without generation: used by the benchmark to measure retrieval quality. */
	@GetMapping("/search")
	List<Source> search(@RequestParam @NotBlank @Size(max = 1000) String q,
			@RequestParam(required = false) String lang, @RequestParam(required = false) Integer limit,
			@RequestParam(defaultValue = "HYBRID") Retrieval.Mode mode,
			@RequestParam(required = false) Double textWeight, @RequestParam(required = false) Double recitalWeight,
			@RequestParam(required = false) Boolean sumLanguages) {
		int n = limit == null ? this.properties.maxSources() : Math.clamp(limit, 1, 50);
		var options = new Retrieval.Options(mode, textWeight, recitalWeight, sumLanguages);
		return this.retrieval.search(q.strip(), language(lang), n, options).sources();
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
