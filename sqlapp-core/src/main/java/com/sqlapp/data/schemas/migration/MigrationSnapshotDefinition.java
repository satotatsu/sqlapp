/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import java.util.HashSet;
import java.util.List;

/** Vendor-neutral slowly-changing-dimension type-2 contract. */
public record MigrationSnapshotDefinition(String id, String tableId, List<String> keyColumns,
		List<String> trackedColumns, String validFromColumn, String validToColumn, String currentColumn,
		boolean expireMissingRows) {

	public MigrationSnapshotDefinition {
		if (id == null || id.isBlank() || tableId == null || tableId.isBlank()) {
			throw new IllegalArgumentException("Snapshot id and tableId are required");
		}
		keyColumns = columns("keyColumns", keyColumns, false);
		trackedColumns = columns("trackedColumns", trackedColumns, false);
		if (validFromColumn == null || validFromColumn.isBlank() || validToColumn == null
				|| validToColumn.isBlank()) {
			throw new IllegalArgumentException("validFromColumn and validToColumn are required");
		}
		currentColumn = currentColumn == null || currentColumn.isBlank() ? null : currentColumn;
		final HashSet<String> names = new HashSet<>(keyColumns);
		if (!names.addAll(trackedColumns)) {
			throw new IllegalArgumentException("keyColumns and trackedColumns must not overlap");
		}
		for (final String system : List.of(validFromColumn, validToColumn)) {
			if (!names.add(system)) {
				throw new IllegalArgumentException("Snapshot system columns must be distinct from data columns: " + system);
			}
		}
		if (currentColumn != null && !names.add(currentColumn)) {
			throw new IllegalArgumentException("currentColumn must be distinct from other snapshot columns");
		}
	}

	private static List<String> columns(final String property, final List<String> values, final boolean allowEmpty) {
		if (values == null || (!allowEmpty && values.isEmpty())
				|| values.stream().anyMatch(value -> value == null || value.isBlank())) {
			throw new IllegalArgumentException(property + " must contain non-empty column names");
		}
		if (new HashSet<>(values).size() != values.size()) {
			throw new IllegalArgumentException(property + " must not contain duplicates");
		}
		return List.copyOf(values);
	}
}
