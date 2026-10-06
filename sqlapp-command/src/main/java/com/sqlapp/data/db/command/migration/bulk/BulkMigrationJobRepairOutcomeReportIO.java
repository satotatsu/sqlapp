/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Atomically reads and writes verified job-repair outcome summaries. */
public final class BulkMigrationJobRepairOutcomeReportIO {
	private static final Set<String> STATUSES = Set.of("SUCCEEDED", "EXECUTION_FAILED", "VERIFICATION_FAILED");

	public void write(final Path file, final BulkMigrationJobRepairOutcomeReport report) {
		validate(report);
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		try {
			final var converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration job repair outcome report: " + absolute, e);
		}
	}

	public BulkMigrationJobRepairOutcomeReport read(final Path file) {
		return read(file, null);
	}

	public BulkMigrationJobRepairOutcomeReport read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).report();
	}

	Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration job repair outcome report does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationJsonFile.read(absolute, maxFileSizeBytes,
					"maxEvidenceFileSizeBytes", "Bulk migration job repair outcome report");
			final var report = validate(new JsonConverter().fromJsonString(new String(bytes, StandardCharsets.UTF_8),
					BulkMigrationJobRepairOutcomeReport.class));
			return new Snapshot(report, fingerprint(bytes));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration job repair outcome report: " + absolute, e);
		}
	}

	record Snapshot(BulkMigrationJobRepairOutcomeReport report, String fingerprint) {
	}

	private static String fingerprint(final byte[] bytes) {
		return "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes);
	}

	static BulkMigrationJobRepairOutcomeReport validate(final BulkMigrationJobRepairOutcomeReport report) {
		if (report == null || report.formatVersion() != BulkMigrationJobRepairOutcomeReport.CURRENT_FORMAT_VERSION
				|| report.generatedAt() == null || !STATUSES.contains(report.status())
				|| blank(report.migrationPlanFingerprint()) || blank(report.repairPlanFingerprint())
				|| !sha256(report.approvedRepairPlanFileFingerprint())
				|| !optionalSha256(report.repairExecutionReportFingerprint())
				|| !optionalSha256(report.repairFailureReportFingerprint())
				|| !optionalSha256(report.postRepairVerificationReportFingerprint())) {
			throw new CommandException("Bulk migration job repair outcome report header is invalid");
		}
		if ("SUCCEEDED".equals(report.status())) {
			if (report.repairExecutionReportFingerprint() == null
					|| report.postRepairVerificationReportFingerprint() == null
					|| report.repairFailureReportFingerprint() != null || report.failurePhase() != null
					|| report.failedTaskId() != null) {
				throw new CommandException("Successful job repair outcome report evidence is invalid");
			}
		} else {
			if (report.repairFailureReportFingerprint() == null || blank(report.failurePhase())
					|| blank(report.failedTaskId())) {
				throw new CommandException("Failed job repair outcome report evidence is invalid");
			}
			if ("EXECUTION_FAILED".equals(report.status())
					&& (report.repairExecutionReportFingerprint() != null
							|| report.postRepairVerificationReportFingerprint() != null
							|| "POST_VERIFICATION".equals(report.failurePhase()))) {
				throw new CommandException("Execution-failed job repair outcome report evidence is invalid");
			}
			if ("VERIFICATION_FAILED".equals(report.status())
					&& (report.postRepairVerificationReportFingerprint() == null
							|| !"POST_VERIFICATION".equals(report.failurePhase()))) {
				throw new CommandException("Verification-failed job repair outcome report evidence is invalid");
			}
		}
		return report;
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	private static boolean optionalSha256(final String value) {
		return value == null || sha256(value);
	}

	private static boolean sha256(final String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}
}
