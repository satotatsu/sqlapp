/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/** Conventional files in one job-repair evidence directory. */
final class BulkMigrationJobRepairReportFiles {
	static final String EXECUTION = "repair-execution.json";
	static final String FAILURE = "repair-failure.json";
	static final String VERIFICATION = "post-repair-verification.json";
	static final String OUTCOME = "repair-outcome.json";

	private BulkMigrationJobRepairReportFiles() {
	}

	static Resolved resolve(final File directory) {
		final Path path = Objects.requireNonNull(directory, "directory").toPath().toAbsolutePath().normalize();
		return new Resolved(path.resolve(EXECUTION).toFile(), path.resolve(FAILURE).toFile(),
				path.resolve(VERIFICATION).toFile(), path.resolve(OUTCOME).toFile());
	}

	record Resolved(File execution, File failure, File verification, File outcome) {
	}
}
