/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

/** Builds a validated migration plan without opening a database connection. */
public final class BulkMigrationJobPlanner {
	private BulkMigrationJobPlanner() {
	}

	public static BulkMigrationJobPlan plan(final List<BulkMigrationJobTask> tasks) {
		return new BulkMigrationJobPlan(tasks);
	}

	public static BulkMigrationJobPlan plan(final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle) {
		return new BulkMigrationJobPlan(tasks, lifecycle);
	}

	public static BulkMigrationJobPlan plan(final String jobId,
			final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle) {
		return new BulkMigrationJobPlan(tasks, lifecycle, jobId);
	}
}
