package eu.fontis.log;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recent answers, for review. Open for now: users and permissions arrive in week 6 and will
 * restrict it to administrators.
 */
@RestController
class AnswerLogController {

	private final AnswerLog log;

	AnswerLogController(AnswerLog log) {
		this.log = log;
	}

	@GetMapping("/api/log")
	List<AnswerLog.Entry> recent(@RequestParam(defaultValue = "50") int limit) {
		return this.log.recent(Math.clamp(limit, 1, 500));
	}

}
