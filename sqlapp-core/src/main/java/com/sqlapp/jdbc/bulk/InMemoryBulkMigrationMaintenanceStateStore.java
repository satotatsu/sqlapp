/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local maintenance state store, primarily for tests. */
public class InMemoryBulkMigrationMaintenanceStateStore
		implements BulkMigrationMaintenanceStateStore {
	private final Map<String, BulkMigrationMaintenanceState> states =
			new ConcurrentHashMap<>();

	@Override
	public Optional<BulkMigrationMaintenanceState> load(final String jobId) {
		validateJobId(jobId);
		return Optional.ofNullable(states.get(jobId));
	}

	@Override
	public void save(final BulkMigrationMaintenanceState state) {
		java.util.Objects.requireNonNull(state, "state");
		states.put(state.jobId(), state);
	}

	@Override
	public void delete(final String jobId) {
		validateJobId(jobId);
		states.remove(jobId);
	}

	private static void validateJobId(final String jobId) {
		if (jobId == null || jobId.isBlank()) {
			throw new IllegalArgumentException("jobId must not be empty");
		}
	}
}
