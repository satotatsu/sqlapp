/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Aggregated repair results for a multi-table migration job. */
@Value
public class BulkMigrationJobRepairResult {
	List<BulkMigrationJobTaskRepairResult> tasks;

	public long getMismatchChunks() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getMismatchChunks).sum();
	}

	public long getReplayedChunks() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getReplayedChunks).sum();
	}

	public long getReplayedRows() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getReplayedRows).sum();
	}

	public long getAffectedRows() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getAffectedRows).sum();
	}

	public long getTasksRequiringManualReconciliation() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.filter(BulkMigrationRepairResult::requiresManualReconciliation).count();
	}

	public boolean requiresManualReconciliation() {
		return getTasksRequiringManualReconciliation() > 0;
	}
}
