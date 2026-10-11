/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.util.List;

/** Immutable summary of a successful bulk migration target validation. */
public record BulkMigrationTargetValidationResult(String planFingerprint, String configurationFingerprint,
		List<String> taskIds) {
	public BulkMigrationTargetValidationResult {
		java.util.Objects.requireNonNull(planFingerprint, "planFingerprint");
		java.util.Objects.requireNonNull(configurationFingerprint, "configurationFingerprint");
		taskIds = List.copyOf(java.util.Objects.requireNonNull(taskIds, "taskIds"));
	}
}
