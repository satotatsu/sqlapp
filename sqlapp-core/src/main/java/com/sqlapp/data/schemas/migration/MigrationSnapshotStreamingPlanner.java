/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** O(1)-comparison-memory SCD2 merge planner for rows ordered by business key. */
public final class MigrationSnapshotStreamingPlanner {
	private MigrationSnapshotStreamingPlanner() {
	}

	public static <E extends Exception> MigrationSnapshotSummary plan(final MigrationSnapshotDefinition definition,
			final Iterable<? extends Map<String, Object>> sourceRows,
			final Iterable<? extends Map<String, Object>> currentRows,
			final MigrationSnapshotChangeHandler<E> handler) throws E {
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(handler, "handler");
		final Cursor source = new Cursor("source", definition, Objects.requireNonNull(sourceRows, "sourceRows").iterator());
		final Cursor current = new Cursor("current target", definition,
				Objects.requireNonNull(currentRows, "currentRows").iterator());
		long inserted = 0;
		long updated = 0;
		long expired = 0;
		long unchanged = 0;
		while (source.hasRow() || current.hasRow()) {
			if (!current.hasRow() || source.hasRow() && source.key().compareTo(current.key()) < 0) {
				handler.accept(change(MigrationSnapshotChange.Type.INSERT, definition, source.row()));
				inserted++;
				source.advance();
			} else if (!source.hasRow() || source.key().compareTo(current.key()) > 0) {
				if (definition.expireMissingRows()) {
					handler.accept(new MigrationSnapshotChange(MigrationSnapshotChange.Type.EXPIRE,
							keyMap(definition, current.row()), Map.of()));
					expired++;
				}
				current.advance();
			} else {
				if (changed(definition, source.row(), current.row())) {
					handler.accept(change(MigrationSnapshotChange.Type.UPDATE_VERSION, definition, source.row()));
					updated++;
				} else {
					unchanged++;
				}
				source.advance();
				current.advance();
			}
		}
		return new MigrationSnapshotSummary(inserted, updated, expired, unchanged);
	}

	private static boolean changed(final MigrationSnapshotDefinition definition, final Map<String, Object> source,
			final Map<String, Object> current) {
		return definition.trackedColumns().stream().anyMatch(column -> !valueEquals(source.get(column), current.get(column)));
	}

	private static boolean valueEquals(final Object left, final Object right) {
		if (left == null || right == null || !left.getClass().isArray() || !right.getClass().isArray()) {
			return Objects.deepEquals(left, right);
		}
		if (Array.getLength(left) != Array.getLength(right)) {
			return false;
		}
		for (int i = 0; i < Array.getLength(left); i++) {
			if (!Objects.deepEquals(Array.get(left, i), Array.get(right, i))) {
				return false;
			}
		}
		return true;
	}

	private static MigrationSnapshotChange change(final MigrationSnapshotChange.Type type,
			final MigrationSnapshotDefinition definition, final Map<String, Object> row) {
		return new MigrationSnapshotChange(type, keyMap(definition, row), row);
	}

	private static Map<String, Object> keyMap(final MigrationSnapshotDefinition definition,
			final Map<String, Object> row) {
		final Map<String, Object> key = new LinkedHashMap<>();
		definition.keyColumns().forEach(column -> key.put(column, row.get(column)));
		return key;
	}

	private static final class Cursor {
		private final String side;
		private final MigrationSnapshotDefinition definition;
		private final Iterator<? extends Map<String, Object>> iterator;
		private Map<String, Object> row;
		private Key key;
		private Key previous;

		private Cursor(final String side, final MigrationSnapshotDefinition definition,
				final Iterator<? extends Map<String, Object>> iterator) {
			this.side = side;
			this.definition = definition;
			this.iterator = iterator;
			advance();
		}

		private boolean hasRow() {
			return row != null;
		}

		private Map<String, Object> row() {
			return row;
		}

		private Key key() {
			return key;
		}

		private void advance() {
			if (!iterator.hasNext()) {
				row = null;
				key = null;
				return;
			}
			row = Objects.requireNonNull(iterator.next(), side + " row");
			final List<Object> values = new ArrayList<>();
			for (final String column : requiredColumns(definition)) {
				if (!row.containsKey(column)) {
					throw new IllegalArgumentException(side + " row is missing column: " + column);
				}
			}
			for (final String column : definition.keyColumns()) {
				final Object value = row.get(column);
				if (value == null) {
					throw new IllegalArgumentException(side + " snapshot key must not contain null");
				}
				values.add(value);
			}
			key = new Key(values);
			if (previous != null && previous.compareTo(key) >= 0) {
				throw new IllegalArgumentException(side + " rows must be strictly ordered by snapshot key");
			}
			previous = key;
		}
	}

	private static List<String> requiredColumns(final MigrationSnapshotDefinition definition) {
		final List<String> columns = new ArrayList<>(definition.keyColumns());
		columns.addAll(definition.trackedColumns());
		return columns;
	}

	private record Key(List<Object> values) implements Comparable<Key> {
		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public int compareTo(final Key other) {
			for (int i = 0; i < values.size(); i++) {
				final Object left = values.get(i);
				final Object right = other.values.get(i);
				if (!(left instanceof Comparable comparable)) {
					throw new IllegalArgumentException("Snapshot key value is not comparable: " + left.getClass().getName());
				}
				final int compared;
				try {
					compared = comparable.compareTo(right);
				} catch (ClassCastException e) {
					throw new IllegalArgumentException("Snapshot key values have incompatible types", e);
				}
				if (compared != 0) {
					return compared;
				}
			}
			return 0;
		}
	}
}
