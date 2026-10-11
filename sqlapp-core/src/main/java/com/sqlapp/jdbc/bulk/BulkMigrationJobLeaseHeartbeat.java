/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Background renewal for chunks that may run longer than a lease interval. */
public final class BulkMigrationJobLeaseHeartbeat implements AutoCloseable {
	private final BulkMigrationJobLeaseManager.LeaseHandle handle;
	private final ScheduledExecutorService executor;
	private final AtomicReference<Throwable> failure = new AtomicReference<>();
	private final Object renewalMonitor = new Object();

	BulkMigrationJobLeaseHeartbeat(
			final BulkMigrationJobLeaseManager.LeaseHandle handle,
			final Duration interval) {
		Objects.requireNonNull(handle, "handle");
		Objects.requireNonNull(interval, "interval");
		this.handle = handle;
		executor = Executors.newSingleThreadScheduledExecutor(runnable ->
				Thread.ofPlatform().daemon().name("sqlapp-bulk-lease-heartbeat").unstarted(
						runnable));
		final long intervalNanos = nanos(interval);
		executor.scheduleWithFixedDelay(() -> {
			synchronized (renewalMonitor) {
				if (failure.get() != null) {
					return;
				}
				try {
					handle.renew();
				} catch (SQLException | RuntimeException e) {
					failure.compareAndSet(null, e);
				}
			}
		}, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
	}

	public void check() {
		synchronized (renewalMonitor) {
			throwIfFailed();
		}
	}

	/**
	 * Serializes with the background heartbeat and renews ownership immediately.
	 * Use this as a commit-time fence so a long JVM or host pause cannot leave the
	 * final commit relying on an old successful heartbeat.
	 */
	public void renewAndCheck() throws SQLException {
		synchronized (renewalMonitor) {
			throwIfFailed();
			try {
				handle.renew();
			} catch (SQLException | RuntimeException e) {
				failure.compareAndSet(null, e);
				throw e;
			}
			throwIfFailed();
		}
	}

	private void throwIfFailed() {
		final Throwable cause = failure.get();
		if (cause != null) {
			throw cause instanceof BulkMigrationJobLeaseLostException lost ? lost
					: new BulkMigrationJobLeaseLostException(cause);
		}
	}

	@Override
	public void close() {
		executor.shutdownNow();
		boolean interrupted = false;
		try {
			while (!executor.isTerminated()) {
				try {
					executor.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static long nanos(final Duration duration) {
		try {
			return duration.toNanos();
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}
}
