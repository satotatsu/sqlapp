/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Aggregated verification results for a multi-table migration job. */
@Value
public class BulkMigrationJobVerificationResult {
	List<BulkMigrationJobTaskVerificationResult> tasks;

	public boolean isMatch() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.allMatch(BulkMigrationVerificationResult::isMatch);
	}

	public long getMismatchedTasks() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.filter(result -> !result.isMatch()).count();
	}

	public long getExpectedRows() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.mapToLong(BulkMigrationVerificationResult::getExpectedRows).sum();
	}

	public long getActualRows() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.mapToLong(BulkMigrationVerificationResult::getActualRows).sum();
	}
}
