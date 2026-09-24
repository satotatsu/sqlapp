/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import java.util.List;

/** Observed execution progress, not a guarantee that database effects were undone. */
public record MigrationExecutionFailure(Phase phase, Long version, String source,
		int attemptedStatement, int completedStatements, boolean nonTransactional,
		List<Long> committedVersions, String sqlState, Integer vendorErrorCode,
		RecoveryOutcome rollback, RecoveryOutcome historyRecovery) {
	public MigrationExecutionFailure {
		committedVersions = List.copyOf(committedVersions);
	}

	public enum Phase {
		SETUP, PRECHECK, MIGRATION, HISTORY_COMPLETION, VERSION_COMMIT, REPEATABLE, FINALIZE, FINAL_COMMIT
	}

	/** RETURNED means the operation returned normally, not that all effects were reversed. */
	public enum RecoveryOutcome {
		NOT_ATTEMPTED, RETURNED, FAILED
	}

	public String recoveryAdvice() {
		return "Inspect the actual schema/data and migration history before retrying. "
				+ "Completed statements may have been rolled back or committed; failed statements may have effects. "
				+ "A returned rollback does not prove DDL or non-transactional SQL was undone. "
				+ "migrationRepair only removes a failed history entry; it does not undo SQL. "
				+ "Resolve partial database changes before repairing history or rerunning SQL.";
	}
}
