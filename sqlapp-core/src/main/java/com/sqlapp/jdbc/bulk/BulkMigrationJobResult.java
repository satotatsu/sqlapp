/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Completed multi-table job results in dependency execution order. */
@Value
public class BulkMigrationJobResult {
	List<BulkMigrationJobTaskResult> tasks;

	public long getProcessedRows() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.mapToLong(ChunkedBulkMigrationResult::getProcessedRows).sum();
	}

	public long getAlreadyCompleteTasks() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.filter(ChunkedBulkMigrationResult::isAlreadyComplete).count();
	}
}
