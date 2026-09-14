/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import lombok.Value;

/** Checkpoints successfully deleted from a validated migration job plan. */
@Value
public class BulkMigrationJobCheckpointResetResult {
	String planFingerprint;
	List<String> resetTaskIds;

	public BulkMigrationJobCheckpointResetResult(final String planFingerprint,
			final List<String> resetTaskIds) {
		if (planFingerprint == null || planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(resetTaskIds, "resetTaskIds");
		if (resetTaskIds.stream().anyMatch(id -> id == null || id.isBlank())
				|| new HashSet<>(resetTaskIds).size() != resetTaskIds.size()) {
			throw new IllegalArgumentException(
					"resetTaskIds must contain unique non-empty task IDs");
		}
		this.planFingerprint = planFingerprint;
		this.resetTaskIds = List.copyOf(resetTaskIds);
	}
}
