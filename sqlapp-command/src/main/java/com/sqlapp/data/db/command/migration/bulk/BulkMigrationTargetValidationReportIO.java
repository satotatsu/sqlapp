/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes live target validation evidence. */
public final class BulkMigrationTargetValidationReportIO {
	public BulkMigrationTargetValidationReport read(final Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			throw new CommandException("Bulk migration target validation report file is required.");
		}
		try {
			return validate(new JsonConverter().fromJsonString(file.toFile(),
					BulkMigrationTargetValidationReport.class));
		} catch (final CommandException e) {
			throw e;
		} catch (final RuntimeException e) {
			throw new CommandException("Could not read bulk migration target validation report: " + file, e);
		}
	}

	public void write(final Path file, final BulkMigrationTargetValidationReport report) {
		if (file == null) {
			throw new CommandException("Bulk migration target validation report output file is required.");
		}
		validate(report);
		try {
			final var converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(file.toAbsolutePath().normalize(),
					temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Could not write bulk migration target validation report: " + file, e);
		}
	}

	private static BulkMigrationTargetValidationReport validate(final BulkMigrationTargetValidationReport report) {
		if (report == null || report.formatVersion() != BulkMigrationTargetValidationReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration target validation report formatVersion.");
		}
		if (report.generatedAt() == null || blank(report.jobId()) || !fingerprint(report.planFingerprint())
				|| !fingerprint(report.configurationFingerprint()) || report.taskIds() == null
				|| report.taskIds().stream().anyMatch(BulkMigrationTargetValidationReportIO::blank)
				|| new HashSet<>(report.taskIds()).size() != report.taskIds().size()
				|| blank(report.databaseProductName()) || blank(report.databaseProductVersion())
				|| report.targetEnvironmentId() != null && report.targetEnvironmentId().isBlank()) {
			throw new CommandException("Bulk migration target validation report contains invalid identities.");
		}
		if (report.provenance() == null
				|| !report.configurationFingerprint().equals(report.provenance().configurationFingerprint())) {
			throw new CommandException("Target validation report provenance does not match configurationFingerprint.");
		}
		return report;
	}

	private static boolean fingerprint(final String value) {
		return value != null && value.matches("(?:sha256:)?[0-9a-f]{64}");
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}
}
