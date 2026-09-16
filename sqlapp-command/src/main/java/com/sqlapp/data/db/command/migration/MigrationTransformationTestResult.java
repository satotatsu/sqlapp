/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

/** Actual and expected canonical rows for one transformation unit test. */
public record MigrationTransformationTestResult(String id, boolean match, List<List<String>> expectedRows,
		List<List<String>> actualRows) {
	public MigrationTransformationTestResult {
		expectedRows = List.copyOf(expectedRows);
		actualRows = List.copyOf(actualRows);
	}
}
