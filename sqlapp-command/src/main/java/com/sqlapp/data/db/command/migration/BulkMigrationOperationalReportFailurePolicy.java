/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

/** Effect of an automatic operational-report failure on the migration job. */
public enum BulkMigrationOperationalReportFailurePolicy {
	FAIL_JOB,
	CONTINUE_JOB
}
