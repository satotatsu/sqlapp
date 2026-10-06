/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;

import com.sqlapp.exceptions.CommandException;

/** Shared external fingerprint gate for an approved job-repair plan file. */
final class BulkMigrationJobRepairApprovalValidator {
	record Validated(BulkMigrationJobRepairPlanReport report, String fingerprint) {
	}

	private BulkMigrationJobRepairApprovalValidator() {
	}

	static Validated validate(final File file, final String expectedFingerprint,
			final Long maxAgeSeconds, final Long maxFileSizeBytes) {
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException(
					"expectedApprovedRepairPlanFileFingerprint must be a lowercase SHA-256 value.");
		}
		if (maxAgeSeconds != null && maxAgeSeconds <= 0) {
			throw new CommandException("maxApprovedRepairPlanAgeSeconds must be greater than zero.");
		}
		final var snapshot = new BulkMigrationJobRepairPlanReportIO().readSnapshot(file.toPath(), maxFileSizeBytes);
		if (expectedFingerprint != null && !expectedFingerprint.equals(snapshot.fingerprint())) {
			throw new CommandException(
					"approvedRepairPlanFile fingerprint does not match expectedApprovedRepairPlanFileFingerprint.");
		}
		final var report = snapshot.report();
		final Instant generatedAt = report.generatedAt();
		final Instant now = Instant.now();
		if (generatedAt.isAfter(now) || report.tasks().stream()
				.anyMatch(task -> task.repairPlan().generatedAt().isAfter(now))) {
			throw new CommandException("Approved repair plan generatedAt is in the future; check clock synchronization.");
		}
		if (maxAgeSeconds != null) {
			try {
				if (generatedAt.plusSeconds(maxAgeSeconds).isBefore(now)) {
					throw new CommandException("Approved repair plan has expired.");
				}
			} catch (DateTimeException | ArithmeticException e) {
				throw new CommandException("maxApprovedRepairPlanAgeSeconds is outside the supported time range.", e);
			}
		}
		return new Validated(report, snapshot.fingerprint());
	}
}
