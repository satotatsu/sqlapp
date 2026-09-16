/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One key-level mutation in an SCD2 snapshot plan. */
public record MigrationSnapshotChange(Type type, Map<String, Object> key, Map<String, Object> sourceRow) {
	public enum Type {
		INSERT, UPDATE_VERSION, EXPIRE
	}

	public MigrationSnapshotChange {
		if (type == null || key == null || key.isEmpty()) {
			throw new IllegalArgumentException("Snapshot change type and key are required");
		}
		key = immutable(key);
		sourceRow = sourceRow == null ? Map.of() : immutable(sourceRow);
		if (type != Type.EXPIRE && sourceRow.isEmpty()) {
			throw new IllegalArgumentException("INSERT and UPDATE_VERSION require a source row");
		}
	}

	private static Map<String, Object> immutable(final Map<String, Object> value) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
