/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes live target validation evidence. */
public final class BulkMigrationTargetValidationReportIO {
	public BulkMigrationTargetValidationReport read(final Path file) {
		return readSnapshot(file).report();
	}

	Snapshot readSnapshot(final Path file) {
		return readSnapshot(file, null);
	}

	Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		if (file == null || !Files.isRegularFile(file)) {
			throw new CommandException("Bulk migration target validation report file is required.");
		}
		try {
			final byte[] bytes = BoundedMigrationFile.read(file, maxFileSizeBytes,
					"maxTargetValidationReportFileSizeBytes", "Target validation report file");
			final var report = validate(new JsonConverter().fromJsonString(new String(bytes, StandardCharsets.UTF_8),
					BulkMigrationTargetValidationReport.class));
			return new Snapshot(report, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
		} catch (final CommandException e) {
			throw e;
		} catch (final IOException | RuntimeException e) {
			throw new CommandException("Could not read bulk migration target validation report: " + file, e);
		}
	}

	record Snapshot(BulkMigrationTargetValidationReport report, String fingerprint) {
		Snapshot {
			Objects.requireNonNull(report, "report");
			Objects.requireNonNull(fingerprint, "fingerprint");
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

	Snapshot writeSnapshot(final Path file, final BulkMigrationTargetValidationReport report) {
		return writeSnapshot(file, report, null);
	}

	Snapshot writeSnapshot(final Path file, final BulkMigrationTargetValidationReport report,
			final Long maxFileSizeBytes) {
		write(file, report);
		final var snapshot = readSnapshot(file, maxFileSizeBytes);
		if (!report.equals(snapshot.report())) {
			throw new CommandException(
					"Written bulk migration target validation report does not match its source model.");
		}
		return snapshot;
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
