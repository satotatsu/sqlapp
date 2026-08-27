/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Completed multi-table job results in dependency execution order. */
@Getter
@EqualsAndHashCode
@ToString
public class BulkMigrationJobResult {
	private final String planFingerprint;
	private final List<BulkMigrationJobTaskResult> tasks;

	public BulkMigrationJobResult(final List<BulkMigrationJobTaskResult> tasks) {
		this(null, tasks);
	}

	public BulkMigrationJobResult(final String planFingerprint,
			final List<BulkMigrationJobTaskResult> tasks) {
		this.planFingerprint = planFingerprint;
		this.tasks = List.copyOf(tasks);
	}

	public long getProcessedRows() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.mapToLong(ChunkedBulkMigrationResult::getProcessedRows).sum();
	}

	public long getAlreadyCompleteTasks() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.filter(ChunkedBulkMigrationResult::isAlreadyComplete).count();
	}
}
