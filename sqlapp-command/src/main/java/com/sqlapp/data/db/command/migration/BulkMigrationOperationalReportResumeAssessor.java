/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;

/** Makes a conservative, read-only resume decision from a validated report. */
public final class BulkMigrationOperationalReportResumeAssessor {
	private static final Set<String> ACTIVE_EVENTS = Set.of("JOB_STARTED",
			"TASK_STARTED", "TASK_COMPLETED");

	private BulkMigrationOperationalReportResumeAssessor() {
	}

	public static BulkMigrationResumeReadiness assess(
			final BulkMigrationOperationalReport report) {
		Objects.requireNonNull(report, "report");
		final BulkMigrationResumeReadiness terminal = terminal(report);
		if (terminal != null) {
			return terminal;
		}
		if (activeEvent(report)) {
			return BulkMigrationResumeReadiness.POSSIBLY_RUNNING;
		}
		if (completionConfirmed(report)) {
			return BulkMigrationResumeReadiness.COMPLETE;
		}
		return BulkMigrationResumeReadiness.RESUMABLE;
	}

	public static BulkMigrationResumeReadiness assess(
			final BulkMigrationOperationalReport report,
			final BulkMigrationJobLease currentLease, final Instant now) {
		Objects.requireNonNull(report, "report");
		Objects.requireNonNull(now, "now");
		if (currentLease != null && !report.jobId().equals(currentLease.jobId())) {
			throw new IllegalArgumentException(
					"Lease jobId does not match the operational report");
		}
		final BulkMigrationResumeReadiness terminal = terminal(report);
		if (terminal != null) {
			return terminal;
		}
		final boolean leaseActive = currentLease != null
				&& !currentLease.isExpiredAt(now);
		if (leaseActive) {
			return BulkMigrationResumeReadiness.POSSIBLY_RUNNING;
		}
		if (completionConfirmed(report)) {
			return BulkMigrationResumeReadiness.COMPLETE;
		}
		return BulkMigrationResumeReadiness.RESUMABLE;
	}

	private static BulkMigrationResumeReadiness terminal(
			final BulkMigrationOperationalReport report) {
		if (!report.compatible() || report.tasks().stream()
				.anyMatch(task -> "INCOMPATIBLE".equals(task.state()))) {
			return BulkMigrationResumeReadiness.INCOMPATIBLE;
		}
		if (report.maintenance() != null && BulkMigrationMaintenanceStatus
				.valueOf(report.maintenance().status()).requiresRecovery()) {
			return BulkMigrationResumeReadiness.RECOVERY_REQUIRED;
		}
		return null;
	}

	private static boolean activeEvent(final BulkMigrationOperationalReport report) {
		return report.execution() != null
				&& ACTIVE_EVENTS.contains(report.execution().event());
	}

	private static boolean completionConfirmed(
			final BulkMigrationOperationalReport report) {
		return report.completedTasks() == report.totalTasks()
				&& (report.execution() == null
						|| "JOB_COMPLETED".equals(report.execution().event()));
	}
}
