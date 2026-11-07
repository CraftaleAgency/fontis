package eu.fontis.ask;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskRateLimiterTests {

	private static final Instant T0 = Instant.parse("2026-11-02T10:00:10Z");

	@Test
	void allowsTheLimitPerMinuteAndResetsTheNextMinute() {
		AskRateLimiter limiter = new AskRateLimiter(2, Clock.fixed(T0, ZoneOffset.UTC));
		assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
		assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
		assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
		assertThat(limiter.tryAcquire("5.6.7.8")).as("other clients are counted separately").isTrue();
		assertThat(limiter.retryAfterSeconds()).isEqualTo(50);

		AskRateLimiter nextMinute = new AskRateLimiter(2, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));
		assertThat(nextMinute.tryAcquire("1.2.3.4")).isTrue();
	}

	@Test
	void zeroDisablesTheLimit() {
		AskRateLimiter limiter = new AskRateLimiter(0, Clock.fixed(T0, ZoneOffset.UTC));
		for (int i = 0; i < 100; i++) {
			assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
		}
	}

}
