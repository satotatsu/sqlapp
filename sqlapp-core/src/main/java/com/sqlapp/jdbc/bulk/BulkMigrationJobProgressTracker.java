/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Tracks independent rate and ETA baselines for every migration in a job. */
public final class BulkMigrationJobProgressTracker
		implements ChunkedBulkMigrationListener {
	private final Map<String, BulkMigrationProgressTracker> trackers;
	private final Map<String, Long> totalRowsByMigration;
	private final Map<String, BulkMigrationProgressSnapshot> snapshots =
			new LinkedHashMap<>();
	private volatile BulkMigrationProgressSnapshot latest;

	public BulkMigrationJobProgressTracker(final Map<String, Long> totalRowsByMigration,
			final Consumer<BulkMigrationProgressSnapshot> consumer) {
		Objects.requireNonNull(totalRowsByMigration, "totalRowsByMigration");
		final Consumer<BulkMigrationProgressSnapshot> target = consumer == null
				? snapshot -> { } : consumer;
		final Map<String, BulkMigrationProgressTracker> values = new LinkedHashMap<>();
		final Map<String, Long> totals = new LinkedHashMap<>();
		totalRowsByMigration.forEach((migrationId, totalRows) -> {
			BulkMigrationCheckpoint.validateMigrationId(migrationId);
			if (values.containsKey(migrationId)) {
				throw new IllegalArgumentException("Duplicate migrationId: " + migrationId);
			}
			values.put(migrationId, new BulkMigrationProgressTracker(totalRows, snapshot -> {
				synchronized (BulkMigrationJobProgressTracker.this) {
					snapshots.put(snapshot.migrationId(), snapshot);
					latest = snapshot;
				}
				target.accept(snapshot);
			}));
			totals.put(migrationId, totalRows);
		});
		this.trackers = Collections.unmodifiableMap(values);
		this.totalRowsByMigration = Collections.unmodifiableMap(totals);
	}

	@Override
	public void onChunkStarted(final ChunkedBulkMigrationProgress progress) {
		tracker(progress).onChunkStarted(progress);
	}

	@Override
	public void onChunkCompleted(final ChunkedBulkMigrationProgress progress) {
		tracker(progress).onChunkCompleted(progress);
	}

	public BulkMigrationProgressSnapshot getLatest() {
		return latest;
	}

	public synchronized Map<String, BulkMigrationProgressSnapshot> getSnapshots() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(snapshots));
	}

	public Map<String, Long> getTotalRowsByMigration() {
		return totalRowsByMigration;
	}

	private BulkMigrationProgressTracker tracker(
			final ChunkedBulkMigrationProgress progress) {
		Objects.requireNonNull(progress, "progress");
		final var tracker = trackers.get(progress.getMigrationId());
		if (tracker == null) {
			throw new IllegalArgumentException("Unknown migrationId in job progress: "
					+ progress.getMigrationId());
		}
		return tracker;
	}
}
