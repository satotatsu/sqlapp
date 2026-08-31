/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Calculates rate and ETA from checkpoint-durable chunk completion events. */
public final class BulkMigrationProgressTracker
		implements ChunkedBulkMigrationListener {
	private final Long totalRows;
	private final Consumer<BulkMigrationProgressSnapshot> consumer;
	private final LongSupplier nanoTime;
	private Long startedAt;
	private Long initialProcessedRows;
	private volatile BulkMigrationProgressSnapshot latest;

	public BulkMigrationProgressTracker(final Long totalRows,
			final Consumer<BulkMigrationProgressSnapshot> consumer) {
		this(totalRows, consumer, System::nanoTime);
	}

	BulkMigrationProgressTracker(final Long totalRows,
			final Consumer<BulkMigrationProgressSnapshot> consumer,
			final LongSupplier nanoTime) {
		if (totalRows != null && totalRows < 0) {
			throw new IllegalArgumentException("totalRows must not be negative");
		}
		this.totalRows = totalRows;
		this.consumer = consumer == null ? snapshot -> { } : consumer;
		this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
	}

	@Override
	public synchronized void onChunkStarted(final ChunkedBulkMigrationProgress progress) {
		start(progress);
	}

	@Override
	public synchronized void onChunkCompleted(final ChunkedBulkMigrationProgress progress) {
		start(progress);
		final long elapsedNanos = Math.max(0, nanoTime.getAsLong() - startedAt);
		final long invocationRows = progress.getProcessedRowsAfter() - initialProcessedRows;
		if (invocationRows < 0) {
			throw new IllegalArgumentException("processed rows moved before the initial checkpoint");
		}
		if (totalRows != null && progress.getProcessedRowsAfter() > totalRows) {
			throw new IllegalArgumentException("processed rows exceed totalRows");
		}
		final double seconds = elapsedNanos / 1_000_000_000d;
		final double rate = seconds == 0 ? 0 : invocationRows / seconds;
		final Double ratio = totalRows == null ? null
				: totalRows == 0 ? 1d
				: (double) progress.getProcessedRowsAfter() / totalRows;
		final Duration remaining;
		if (totalRows == null || rate == 0) {
			remaining = null;
		} else {
			final double remainingSeconds =
					(totalRows - progress.getProcessedRowsAfter()) / rate;
			remaining = Duration.ofNanos((long) (remainingSeconds * 1_000_000_000d));
		}
		latest = new BulkMigrationProgressSnapshot(progress.getMigrationId(),
				progress.getProcessedRowsAfter(), totalRows,
				Duration.ofNanos(elapsedNanos), rate, ratio, remaining);
		consumer.accept(latest);
	}

	public BulkMigrationProgressSnapshot getLatest() {
		return latest;
	}

	private void start(final ChunkedBulkMigrationProgress progress) {
		Objects.requireNonNull(progress, "progress");
		if (startedAt == null) {
			startedAt = nanoTime.getAsLong();
			initialProcessedRows = progress.getProcessedRowsBefore();
		}
	}
}
