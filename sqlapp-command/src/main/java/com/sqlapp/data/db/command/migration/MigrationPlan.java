/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

/** Immutable, read-only description of a versioned migration invocation. */
public record MigrationPlan(boolean historyExists, Long currentVersion, Long targetVersion,
		int setupStatements, int finalizeStatements, List<Entry> pending,
		List<HistoryIssue> historyIssues, MigrationValidationResult checksumValidation) {
	public MigrationPlan {
		pending = List.copyOf(pending);
		historyIssues = List.copyOf(historyIssues);
	}

	public record Entry(long version, String description, String source, int statements,
			boolean transactional, boolean checksumWillBeRecorded) {
	}

	public record HistoryIssue(long version, Status status) {
	}

	public boolean hasBlockers() {
		return !historyIssues.isEmpty()
				|| checksumValidation != null && checksumValidation.hasFailures();
	}
}
