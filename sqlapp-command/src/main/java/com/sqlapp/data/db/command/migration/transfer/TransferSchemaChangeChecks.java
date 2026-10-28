/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

/**
 * Stable category for explicit authorization of potentially data-impacting DDL.
 */
public final class TransferSchemaChangeChecks {
	public static final String DATA_IMPACT = "[SCHEMA_DATA_IMPACT]";
	public static final String UNRESOLVED = "[SCHEMA_DIFF_UNRESOLVED]";

	private TransferSchemaChangeChecks() {
	}

	/**
	 * Review may contain the SQL even when execution of these changes is disabled.
	 */
	public static boolean executionBlocked(TransferJobConfiguration configuration,
			TransferSchemaPreparation.Plan plan) {
		return !configuration.allowDestructiveSchemaChanges()
				&& plan.warnings().stream().anyMatch(warning -> warning.startsWith(DATA_IMPACT));
	}

	public static long unresolvedCount(TransferSchemaPreparation.Plan plan) {
		return plan.warnings().stream().filter(warning -> warning.startsWith(UNRESOLVED)).count();
	}
}
