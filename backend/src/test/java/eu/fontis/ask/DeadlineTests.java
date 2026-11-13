package eu.fontis.ask;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlineTests {

	@Test
	void workThatFinishesInTimeReturnsItsResult() {
		assertThat(Deadline.run(Duration.ofSeconds(5), () -> "answer")).isEqualTo("answer");
		assertThat(Thread.currentThread().isInterrupted()).isFalse();
	}

	@Test
	void aBlockedWaitIsInterruptedAtTheDeadline() {
		// Like the HTTP client waiting forever on a dead connection.
		CompletableFuture<String> never = new CompletableFuture<>();
		long start = System.nanoTime();
		assertThatThrownBy(() -> Deadline.run(Duration.ofMillis(200), () -> {
			try {
				return never.get();
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		})).isInstanceOf(Deadline.DeadlineExceededException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
		// The server thread is not left interrupted.
		assertThat(Thread.currentThread().isInterrupted()).isFalse();
	}

}
