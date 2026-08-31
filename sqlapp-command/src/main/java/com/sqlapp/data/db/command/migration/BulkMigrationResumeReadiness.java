/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

/** Conservative resume decision derived from an operational report. */
public enum BulkMigrationResumeReadiness {
	COMPLETE,
	RESUMABLE,
	POSSIBLY_RUNNING,
	RECOVERY_REQUIRED,
	INCOMPATIBLE
}
