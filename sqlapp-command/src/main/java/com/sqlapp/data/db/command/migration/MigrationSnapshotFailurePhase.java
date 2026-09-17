/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

/** Point at which an SCD2 command failed. */
public enum MigrationSnapshotFailurePhase {
	/** Target execution failed; the executor rolled its target transaction back. */
	DATABASE_EXECUTION,
	/**
	 * Target execution committed, but command finalization failed before success
	 * publication.
	 */
	POST_COMMIT_FINALIZATION,
	/**
	 * Target execution committed, but the success artifact could not be published.
	 */
	SUCCESS_REPORT_WRITE
}
