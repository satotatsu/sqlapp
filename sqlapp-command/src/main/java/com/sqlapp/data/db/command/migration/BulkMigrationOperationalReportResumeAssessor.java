/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.Objects;
import java.util.Set;

/** Makes a conservative, read-only resume decision from a validated report. */
public final class BulkMigrationOperationalReportResumeAssessor {
	private static final Set<String> RECOVERY_STATUSES = Set.of("PREPARING",
			"PREPARED", "POST_PROCESSING", "RESTORING", "RESTORE_FAILED");
	private static final Set<String> ACTIVE_EVENTS = Set.of("JOB_STARTED",
			"TASK_STARTED", "TASK_COMPLETED");

	private BulkMigrationOperationalReportResumeAssessor() {
	}

	public static BulkMigrationResumeReadiness assess(
			final BulkMigrationOperationalReport report) {
		Objects.requireNonNull(report, "report");
		if (!report.compatible() || report.tasks().stream()
				.anyMatch(task -> "INCOMPATIBLE".equals(task.state()))) {
			return BulkMigrationResumeReadiness.INCOMPATIBLE;
		}
		if (report.maintenance() != null && RECOVERY_STATUSES
				.contains(report.maintenance().status())) {
			return BulkMigrationResumeReadiness.RECOVERY_REQUIRED;
		}
		if (report.completedTasks() == report.totalTasks()) {
			return BulkMigrationResumeReadiness.COMPLETE;
		}
		if (report.execution() != null
				&& ACTIVE_EVENTS.contains(report.execution().event())) {
			return BulkMigrationResumeReadiness.POSSIBLY_RUNNING;
		}
		return BulkMigrationResumeReadiness.RESUMABLE;
	}
}
