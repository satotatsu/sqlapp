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
	public Optional<BulkMigrationMaintenanceState> load(final String planFingerprint) {
		return Optional.ofNullable(states.get(planFingerprint));
	}

	@Override
	public void save(final BulkMigrationMaintenanceState state) {
		states.put(state.planFingerprint(), state);
	}

	@Override
	public void delete(final String planFingerprint) {
		states.remove(planFingerprint);
	}
}
