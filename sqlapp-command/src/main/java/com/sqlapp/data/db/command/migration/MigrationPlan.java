/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

import com.sqlapp.data.schemas.migration.SchemaCompatibilityReport;

/** Immutable, read-only description of a versioned migration invocation. */
public record MigrationPlan(boolean historyExists, Long currentVersion, Long targetVersion,
		int setupStatements, int finalizeStatements, List<Entry> pending,
		List<HistoryIssue> historyIssues, MigrationValidationResult checksumValidation,
		SchemaCompatibilityReport schemaDrift, List<Long> outOfOrderVersions, boolean outOfOrderRejected,
		boolean nonTransactionalRejected) {
	public MigrationPlan {
		pending = List.copyOf(pending);
		historyIssues = List.copyOf(historyIssues);
		outOfOrderVersions = List.copyOf(outOfOrderVersions);
	}

	public MigrationPlan(final boolean historyExists, final Long currentVersion, final Long targetVersion,
			final int setupStatements, final int finalizeStatements, final List<Entry> pending,
			final List<HistoryIssue> historyIssues, final MigrationValidationResult checksumValidation,
			final SchemaCompatibilityReport schemaDrift) {
		this(historyExists, currentVersion, targetVersion, setupStatements, finalizeStatements, pending,
				historyIssues, checksumValidation, schemaDrift, List.of(), false, false);
	}

	/** Retains the constructor used before schema-drift reporting was added. */
	public MigrationPlan(final boolean historyExists, final Long currentVersion, final Long targetVersion,
			final int setupStatements, final int finalizeStatements, final List<Entry> pending,
			final List<HistoryIssue> historyIssues, final MigrationValidationResult checksumValidation) {
		this(historyExists, currentVersion, targetVersion, setupStatements, finalizeStatements, pending,
				historyIssues, checksumValidation, null);
	}

	public record Entry(long version, String description, String source, int statements,
			boolean transactional, boolean checksumWillBeRecorded) {
	}

	public record HistoryIssue(long version, Status status) {
	}

	public boolean hasBlockers() {
		return !historyIssues.isEmpty()
				|| checksumValidation != null && checksumValidation.hasFailures()
				|| schemaDrift != null && !schemaDrift.isCompatible()
				|| outOfOrderRejected && !outOfOrderVersions.isEmpty()
				|| nonTransactionalRejected && pending.stream().anyMatch(entry -> !entry.transactional());
	}
}
