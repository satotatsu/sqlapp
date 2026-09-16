/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.util.List;

import com.sqlapp.data.schemas.migration.MigrationDataTest;

/** Stable result of one migration data test. */
public record MigrationDataTestResult(String id, MigrationDataTest.Type type, MigrationDataTest.Severity severity,
		Status status, long failures, List<List<Object>> samples) {

	public MigrationDataTestResult {
		samples = List.copyOf(samples);
	}

	public enum Status {
		PASS, WARN, ERROR
	}
}
