/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Computes deterministic SCD2 changes from source rows and current target rows. */
public final class MigrationSnapshotPlanner {
	private MigrationSnapshotPlanner() {
	}

	public static MigrationSnapshotPlan plan(final MigrationSnapshotDefinition definition, final Instant effectiveAt,
			final Iterable<? extends Map<String, Object>> sourceRows,
			final Iterable<? extends Map<String, Object>> currentRows) {
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(effectiveAt, "effectiveAt");
		final Map<Key, Map<String, Object>> source = index("source", definition, sourceRows);
		final Map<Key, Map<String, Object>> current = index("current target", definition, currentRows);
		final List<MigrationSnapshotChange> changes = new ArrayList<>();
		long unchanged = 0;
		for (final var entry : source.entrySet()) {
			final Map<String, Object> existing = current.remove(entry.getKey());
			if (existing == null) {
				changes.add(change(MigrationSnapshotChange.Type.INSERT, definition, entry.getValue()));
			} else if (changed(definition, entry.getValue(), existing)) {
				changes.add(change(MigrationSnapshotChange.Type.UPDATE_VERSION, definition, entry.getValue()));
			} else {
				unchanged++;
			}
		}
		if (definition.expireMissingRows()) {
			for (final Map<String, Object> row : current.values()) {
				changes.add(new MigrationSnapshotChange(MigrationSnapshotChange.Type.EXPIRE, key(definition, row), Map.of()));
			}
		}
		return new MigrationSnapshotPlan(definition, effectiveAt, changes, unchanged);
	}

	private static Map<Key, Map<String, Object>> index(final String side, final MigrationSnapshotDefinition definition,
			final Iterable<? extends Map<String, Object>> rows) {
		Objects.requireNonNull(rows, side + " rows");
		final Map<Key, Map<String, Object>> indexed = new LinkedHashMap<>();
		for (final Map<String, Object> row : rows) {
			Objects.requireNonNull(row, side + " row");
			for (final String column : requiredColumns(definition)) {
				if (!row.containsKey(column)) {
					throw new IllegalArgumentException(side + " row is missing column: " + column);
				}
			}
			final List<Object> keyValues = definition.keyColumns().stream().map(row::get).toList();
			if (keyValues.stream().anyMatch(Objects::isNull)) {
				throw new IllegalArgumentException(side + " snapshot key must not contain null: " + keyValues);
			}
			final Key key = new Key(keyValues);
			if (indexed.putIfAbsent(key, row) != null) {
				throw new IllegalArgumentException("Duplicate " + side + " snapshot key: " + key.values());
			}
		}
		return indexed;
	}

	private static List<String> requiredColumns(final MigrationSnapshotDefinition definition) {
		final List<String> columns = new ArrayList<>(definition.keyColumns());
		columns.addAll(definition.trackedColumns());
		return columns;
	}

	private static boolean changed(final MigrationSnapshotDefinition definition, final Map<String, Object> source,
			final Map<String, Object> current) {
		return definition.trackedColumns().stream().anyMatch(column -> !valueEquals(source.get(column), current.get(column)));
	}

	private static boolean valueEquals(final Object left, final Object right) {
		if (left == null || right == null || !left.getClass().isArray() || !right.getClass().isArray()) {
			return Objects.deepEquals(left, right);
		}
		final int length = Array.getLength(left);
		if (length != Array.getLength(right)) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			if (!Objects.deepEquals(Array.get(left, i), Array.get(right, i))) {
				return false;
			}
		}
		return true;
	}

	private static MigrationSnapshotChange change(final MigrationSnapshotChange.Type type,
			final MigrationSnapshotDefinition definition, final Map<String, Object> row) {
		return new MigrationSnapshotChange(type, key(definition, row), row);
	}

	private static Map<String, Object> key(final MigrationSnapshotDefinition definition,
			final Map<String, Object> row) {
		final Map<String, Object> key = new LinkedHashMap<>();
		definition.keyColumns().forEach(column -> key.put(column, row.get(column)));
		return key;
	}

	private record Key(List<Object> values) {
		private Key {
			values = new ArrayList<>(values);
		}

		@Override
		public boolean equals(final Object obj) {
			return obj instanceof Key other && Arrays.deepEquals(values.toArray(), other.values.toArray());
		}

		@Override
		public int hashCode() {
			return Arrays.deepHashCode(values.toArray());
		}
	}
}
