/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

/**
 * Stable Schema warning categories and authorization checks for data-impacting
 * DDL.
 */
public final class TransferSchemaChangeChecks {
	public static final String DATA_IMPACT = "[SCHEMA_DATA_IMPACT]";
	public static final String UNRESOLVED = "[SCHEMA_DIFF_UNRESOLVED]";
	/**
	 * Target CREATE names adjusted to avoid collisions; definitions are retained.
	 */
	public static final String NAME_ADJUSTMENT = "[SCHEMA_NAME_ADJUSTED]";
	/** Name mapping retains opaque source SQL expressions verbatim. */
	public static final String MAPPING_EXPRESSIONS = "[SCHEMA_MAPPING_EXPRESSIONS]";
	/**
	 * A column DEFAULT was translated for the target, possibly with reported
	 * differences.
	 */
	public static final String DEFAULT_CONVERTED = "[SCHEMA_DEFAULT_CONVERTED]";
	/**
	 * A column DEFAULT could not be translated and is omitted from the target until
	 * corrected or accepted.
	 */
	public static final String DEFAULT_UNRESOLVED = "[SCHEMA_DEFAULT_UNRESOLVED]";

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

	/** Number of column defaults that remain open before cutover. */
	public static long openDefaultCount(TransferSchemaPreparation.Plan plan) {
		return plan.defaults().stream().filter(TransferDefaultConversion::isOpen).count();
	}
}
