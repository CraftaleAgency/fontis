package eu.fontis.ask;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Interrupts work on the current thread that runs past a deadline. The HTTP client's own timeouts
 * are not enough: a request to the model server over a connection that died silently was seen
 * waiting for over an hour, holding one of the few generation slots. Interrupting the thread ends
 * the blocked wait; the work then fails with an exception the caller turns into a 503.
 * <p>
 * The work stays on the calling thread, so the trace context of the question is kept.
 */
final class Deadline {

	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "fontis-deadline");
		thread.setDaemon(true);
		return thread;
	});

	private Deadline() {
	}

	static <T> T run(Duration timeout, Supplier<T> work) {
		Thread worker = Thread.currentThread();
		Object lock = new Object();
		// [0]: the work finished; [1]: the deadline fired. Not the interrupt status, which a caught
		// InterruptedException clears.
		boolean[] state = { false, false };
		ScheduledFuture<?> alarm = TIMER.schedule(() -> {
			synchronized (lock) {
				if (!state[0]) {
					state[1] = true;
					worker.interrupt();
				}
			}
		}, timeout.toMillis(), TimeUnit.MILLISECONDS);
		try {
			// Work that completed despite a late interrupt keeps its result.
			return work.get();
		}
		catch (RuntimeException ex) {
			if (fired(lock, state)) {
				throw new DeadlineExceededException(timeout, ex);
			}
			throw ex;
		}
		finally {
			// After this, the alarm can no longer interrupt this thread.
			synchronized (lock) {
				state[0] = true;
			}
			alarm.cancel(false);
			// Never leave an interrupt behind on a pooled server thread.
			Thread.interrupted();
		}
	}

	/** Marks the work finished and tells whether the deadline fired first. */
	private static boolean fired(Object lock, boolean[] state) {
		synchronized (lock) {
			state[0] = true;
			return state[1];
		}
	}

	static final class DeadlineExceededException extends RuntimeException {

		DeadlineExceededException(Duration timeout, Throwable cause) {
			super("No answer within " + timeout.toSeconds() + " s", cause);
		}

	}

}
