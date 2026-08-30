/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

/** Dry-run description of one migration lifecycle operation. */
public record BulkMigrationJobOperation(String id,
		BulkMigrationJobOperationPhase phase, String description,
		boolean transactionBreaking) {
	public BulkMigrationJobOperation {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("operation id must not be empty");
		}
		Objects.requireNonNull(phase, "phase");
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("operation description must not be empty");
		}
	}
}
