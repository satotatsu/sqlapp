/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

import com.sqlapp.data.schemas.migration.SchemaCompatibilityReport;

/** Immutable, read-only description of a versioned migration invocation. */
public record MigrationPlan(boolean historyExists, Long currentVersion, Long targetVersion,
		int setupStatements, int finalizeStatements, List<Entry> pending,
		List<HistoryIssue> historyIssues, MigrationValidationResult checksumValidation,
		SchemaCompatibilityReport schemaDrift, List<Long> outOfOrderVersions, boolean outOfOrderRejected,
		boolean nonTransactionalRejected, boolean downMigrationRequired, DatabaseIdentity databaseIdentity,
		List<RepeatableEntry> pendingRepeatables) {
	public MigrationPlan {
		pending = List.copyOf(pending);
		historyIssues = List.copyOf(historyIssues);
		outOfOrderVersions = List.copyOf(outOfOrderVersions);
		pendingRepeatables = pendingRepeatables == null ? List.of() : List.copyOf(pendingRepeatables);
	}

	/** Retains the constructor used before repeatable migrations were added. */
	public MigrationPlan(final boolean historyExists, final Long currentVersion, final Long targetVersion,
			final int setupStatements, final int finalizeStatements, final List<Entry> pending,
			final List<HistoryIssue> historyIssues, final MigrationValidationResult checksumValidation,
			final SchemaCompatibilityReport schemaDrift, final List<Long> outOfOrderVersions,
			final boolean outOfOrderRejected, final boolean nonTransactionalRejected,
			final boolean downMigrationRequired, final DatabaseIdentity databaseIdentity) {
		this(historyExists, currentVersion, targetVersion, setupStatements, finalizeStatements, pending,
				historyIssues, checksumValidation, schemaDrift, outOfOrderVersions, outOfOrderRejected,
				nonTransactionalRejected, downMigrationRequired, databaseIdentity, List.of());
	}

	public MigrationPlan(final boolean historyExists, final Long currentVersion, final Long targetVersion,
			final int setupStatements, final int finalizeStatements, final List<Entry> pending,
			final List<HistoryIssue> historyIssues, final MigrationValidationResult checksumValidation,
			final SchemaCompatibilityReport schemaDrift) {
		this(historyExists, currentVersion, targetVersion, setupStatements, finalizeStatements, pending,
				historyIssues, checksumValidation, schemaDrift, List.of(), false, false, false, null, List.of());
	}

	/** Retains the constructor used before schema-drift reporting was added. */
	public MigrationPlan(final boolean historyExists, final Long currentVersion, final Long targetVersion,
			final int setupStatements, final int finalizeStatements, final List<Entry> pending,
			final List<HistoryIssue> historyIssues, final MigrationValidationResult checksumValidation) {
		this(historyExists, currentVersion, targetVersion, setupStatements, finalizeStatements, pending,
				historyIssues, checksumValidation, null);
	}

	public record Entry(long version, String description, String source, int statements,
			boolean transactional, boolean checksumWillBeRecorded, boolean rollbackAvailable, String sourceChecksum) {
		public Entry(final long version, final String description, final String source, final int statements,
				final boolean transactional, final boolean checksumWillBeRecorded, final boolean rollbackAvailable) {
			this(version, description, source, statements, transactional, checksumWillBeRecorded, rollbackAvailable, null);
		}

		public Entry(final long version, final String description, final String source, final int statements,
				final boolean transactional, final boolean checksumWillBeRecorded) {
			this(version, description, source, statements, transactional, checksumWillBeRecorded, false, null);
		}
	}

	public record HistoryIssue(long version, Status status) {
	}

	public record RepeatableEntry(String name, String source, int statements, boolean transactional,
			String sourceChecksum, String previousChecksum) {
	}

	/** Non-secret identity of the database used to create the plan. */
	public record DatabaseIdentity(String productName, String productVersion, String connectionFingerprint) {
	}

	public boolean hasBlockers() {
		return !historyIssues.isEmpty()
				|| checksumValidation != null && checksumValidation.hasFailures()
				|| schemaDrift != null && !schemaDrift.isCompatible()
				|| outOfOrderRejected && !outOfOrderVersions.isEmpty()
				|| nonTransactionalRejected && (pending.stream().anyMatch(entry -> !entry.transactional())
						|| pendingRepeatables.stream().anyMatch(entry -> !entry.transactional()))
				|| downMigrationRequired && pending.stream().anyMatch(entry -> !entry.rollbackAvailable());
	}
}
