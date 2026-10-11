/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Portable evidence that a declarative job passed live target validation. */
public record BulkMigrationTargetValidationReport(int formatVersion, Instant generatedAt, String jobId,
		String planFingerprint, String configurationFingerprint, List<String> taskIds,
		BulkMigrationArtifactProvenance provenance, String targetEnvironmentId, String databaseProductName,
		String databaseProductVersion, String catalogName, String schemaName) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public BulkMigrationTargetValidationReport {
		Objects.requireNonNull(generatedAt, "generatedAt");
		taskIds = List.copyOf(Objects.requireNonNull(taskIds, "taskIds"));
	}
}
