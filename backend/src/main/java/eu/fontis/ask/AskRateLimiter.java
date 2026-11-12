package eu.fontis.ask;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Requests per client per minute, so a public demo cannot saturate the GPU (questions) or be used
 * to guess passwords (logins). Fixed one-minute
 * windows, kept in memory only: client addresses are never stored on disk or logged.
 */
@Component
public class AskRateLimiter {

	private record Window(long minute, int count) {
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private final int perMinute;

	private final Clock clock;

	@Autowired
	public AskRateLimiter(@Value("${fontis.ask.rate-limit-per-minute}") int perMinute) {
		this(perMinute, Clock.systemUTC());
	}

	AskRateLimiter(int perMinute, Clock clock) {
		this.perMinute = perMinute;
		this.clock = clock;
	}

	/** Counts a question from this client; false when the client is over the limit (0 disables it). */
	public boolean tryAcquire(String client) {
		if (this.perMinute <= 0) {
			return true;
		}
		long minute = this.clock.millis() / 60_000;
		if (this.windows.size() > 10_000) {
			this.windows.values().removeIf(w -> w.minute() < minute);
		}
		Window window = this.windows.merge(client, new Window(minute, 1),
				(old, fresh) -> old.minute() == minute ? new Window(minute, old.count() + 1) : fresh);
		return window.count() <= this.perMinute;
	}

	/** Whether this client is already at the limit, without counting a request. */
	public boolean exhausted(String client) {
		Window window = this.windows.get(client);
		return this.perMinute > 0 && window != null && window.minute() == this.clock.millis() / 60_000
				&& window.count() >= this.perMinute;
	}

	/** Seconds until the current window ends. */
	public long retryAfterSeconds() {
		return 60 - (this.clock.millis() / 1000) % 60;
	}

}
