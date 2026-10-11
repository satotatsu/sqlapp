/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;

/** Maintains duplicate-key selection across migration chunk boundaries. */
final class BulkUpsertDuplicateTracker {
	private final List<Column> keys;
	private final BulkUpsertDuplicateKeyStrategy strategy;
	private final BulkUpsertDuplicateRowSelector selector;
	private final Map<Key, Retained> retained = new HashMap<>();
	private long rowNumber;

	BulkUpsertDuplicateTracker(final BulkUpsertPlan plan) {
		keys = plan.getKeyColumns();
		strategy = plan.getOption().getDuplicateKeyStrategy();
		selector = plan.getOption().getDuplicateRowSelector();
	}

	void skip(final Row row) {
		filter(List.of(row), false);
	}

	List<Row> filter(final List<Row> rows) {
		return filter(rows, true);
	}

	private List<Row> filter(final List<Row> rows, final boolean emit) {
		final List<Row> output = new ArrayList<>(rows.size());
		final Map<Key, Integer> outputIndexes = new LinkedHashMap<>();
		for (final Row candidate : rows) {
			rowNumber++;
			final Key key = key(candidate);
			if (key == null) {
				if (emit) {
					output.add(candidate);
				}
				continue;
			}
			final Retained current = retained.get(key);
			if (current == null) {
				retained.put(key, retained(candidate, rowNumber));
				if (emit) {
					outputIndexes.put(key, output.size());
					output.add(candidate);
				}
				continue;
			}
			switch (strategy) {
			case ERROR -> throw new IllegalArgumentException(
					"Duplicate bulk upsert key at source rows " + current.firstRow()
							+ " and " + rowNumber);
			case KEEP_FIRST -> { }
			case KEEP_LAST -> retainCandidate(key, candidate, current.firstRow(), emit,
					output, outputIndexes);
			case CUSTOM -> {
				final Row selected = selector.select(current.row(), candidate);
				if (selected != current.row() && selected != candidate) {
					throw new IllegalArgumentException("duplicateRowSelector must return retained "
							+ "or candidate at source row " + rowNumber);
				}
				if (selected == candidate) {
					retainCandidate(key, candidate, current.firstRow(), emit,
							output, outputIndexes);
				}
			}
			}
		}
		return output;
	}

	private void retainCandidate(final Key key, final Row candidate, final long firstRow,
			final boolean emit, final List<Row> output,
			final Map<Key, Integer> outputIndexes) {
		retained.put(key, retained(candidate, firstRow));
		if (!emit) {
			return;
		}
		final Integer index = outputIndexes.get(key);
		if (index == null) {
			outputIndexes.put(key, output.size());
			output.add(candidate);
		} else {
			output.set(index, candidate);
		}
	}

	private Retained retained(final Row row, final long firstRow) {
		return new Retained(strategy == BulkUpsertDuplicateKeyStrategy.CUSTOM ? row : null,
				firstRow);
	}

	private Key key(final Row row) {
		final Object[] values = new Object[keys.size()];
		for (int i = 0; i < keys.size(); i++) {
			values[i] = row.get(keys.get(i));
			if (values[i] == null) {
				return null;
			}
		}
		return new Key(values);
	}

	private record Retained(Row row, long firstRow) { }

	private static final class Key {
		private final Object[] values;
		private final int hash;

		private Key(final Object[] values) {
			this.values = normalize(values);
			this.hash = Arrays.deepHashCode(this.values);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(final Object obj) {
			return obj instanceof Key other && Arrays.deepEquals(values, other.values);
		}

		private static Object[] normalize(final Object[] values) {
			final Object[] result = values.clone();
			for (int i = 0; i < result.length; i++) {
				final Object value = result[i];
				if (value != null && value.getClass().isArray() && !(value instanceof Object[])) {
					final int length = Array.getLength(value);
					final Object[] array = new Object[length];
					for (int j = 0; j < length; j++) {
						array[j] = Array.get(value, j);
					}
					result[i] = array;
				}
			}
			return result;
		}
	}
}
