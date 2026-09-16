/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import java.time.Instant;
import java.util.List;

/** Immutable SCD2 plan evaluated at one effective timestamp. */
public record MigrationSnapshotPlan(MigrationSnapshotDefinition definition, Instant effectiveAt,
		List<MigrationSnapshotChange> changes, long unchangedRows) {
	public MigrationSnapshotPlan {
		if (definition == null || effectiveAt == null || unchangedRows < 0) {
			throw new IllegalArgumentException("Snapshot definition, timestamp, and non-negative count are required");
		}
		changes = List.copyOf(changes);
	}

	public long count(final MigrationSnapshotChange.Type type) {
		return changes.stream().filter(change -> change.type() == type).count();
	}
}
