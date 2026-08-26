/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/** Resolved columns and streaming input validation shared by bulk upserts. */
public final class BulkUpsertPlan {
	private final Table table;
	private final BulkUpsertOption option;
	private final List<Column> keyColumns;
	private final List<Column> stagingColumns;
	private final List<Column> updateColumns;

	public static BulkUpsertPlan resolve(final Table table, final BulkUpsertOption options) {
		return new BulkUpsertPlan(table, options == null ? BulkUpsertOption.defaults() : options);
	}

	private BulkUpsertPlan(final Table table, final BulkUpsertOption option) {
		this.table = java.util.Objects.requireNonNull(table, "table");
		this.option = java.util.Objects.requireNonNull(option, "option");
		if (!option.isUpdateWhenMatched() && !option.isInsertWhenNotMatched())
			throw new IllegalArgumentException("At least one upsert action must be enabled");
		keyColumns = List.copyOf(resolveKeys());
		stagingColumns = List.copyOf(resolveStagingColumns());
		updateColumns = List.copyOf(resolveUpdateColumns());
		if (option.isUpdateWhenMatched() && updateColumns.isEmpty() && !option.isInsertWhenNotMatched())
			throw new IllegalArgumentException("No columns are available to update");
	}

	public BulkUpsertOption getOption() { return option; }
	public List<Column> getKeyColumns() { return keyColumns; }
	public List<Column> getStagingColumns() { return stagingColumns; }
	public List<Column> getUpdateColumns() { return updateColumns; }

	/** Creates a staging model whose iterator rejects duplicate non-null keys. */
	public Table createStagingTable(final String name) {
		final Table staging = new Table(name);
		final Set<String> included = names(stagingColumns);
		for (final Column column : table.getColumns()) {
			final Column copy = column.clone().setIdentity(false);
			if (!included.contains(column.getName())) copy.setHidden(true);
			staging.getColumns().add(copy);
		}
		staging.setRowIteratorHandler(rows -> new UniqueKeyIterator(table.getRows().iterator(), keyColumns,
				option.getDuplicateKeyStrategy(), option.getDuplicateRowSelector()));
		return staging;
	}

	private List<Column> resolveKeys() {
		final List<String> names = new ArrayList<>(option.getKeyColumns());
		if (names.isEmpty()) {
			if (table.getPrimaryKeyConstraint() == null || table.getPrimaryKeyConstraint().getColumns().isEmpty())
				throw new IllegalArgumentException("Bulk upsert requires keyColumns or a primary key: " + table.getName());
			table.getPrimaryKeyConstraint().getColumns().forEach(c -> names.add(c.getName()));
		}
		final List<Column> result = columns(names, "key");
		if (result.stream().anyMatch(Column::isIdentity) && !option.getBulkOption().isKeepIdentity())
			throw new IllegalArgumentException("An identity key requires bulkOption.keepIdentity=true");
		return result;
	}

	private List<Column> resolveStagingColumns() {
		final Set<String> keys = names(keyColumns);
		final List<Column> result = new ArrayList<>();
		for (final Column column : table.getColumns())
			if (!column.isHidden() && CommonUtils.isEmpty(column.getFormula())
					&& (!column.isIdentity() || option.getBulkOption().isKeepIdentity() || keys.contains(column.getName())))
				result.add(column);
		if (!names(result).containsAll(keys))
			throw new IllegalArgumentException("Every key column must be writable to the staging table");
		return result;
	}

	private List<Column> resolveUpdateColumns() {
		final Set<String> keys = names(keyColumns), staged = names(stagingColumns);
		if (!option.getUpdateColumns().isEmpty()) {
			final List<Column> result = columns(option.getUpdateColumns(), "update");
			for (final Column column : result)
				if (keys.contains(column.getName()) || column.isIdentity() || !staged.contains(column.getName()))
					throw new IllegalArgumentException("Invalid bulk upsert update column: " + column.getName());
			return result;
		}
		final List<Column> result = new ArrayList<>();
		for (final Column column : stagingColumns)
			if (!keys.contains(column.getName()) && !column.isIdentity()) result.add(column);
		return result;
	}

	private List<Column> columns(final List<String> names, final String role) {
		final List<Column> result = new ArrayList<>();
		final Set<String> unique = new HashSet<>();
		for (final String name : names) {
			final Column column = table.getColumns().get(name);
			if (column == null) throw new IllegalArgumentException("Unknown bulk upsert " + role + " column: " + name);
			if (!unique.add(column.getName()))
				throw new IllegalArgumentException("Duplicate bulk upsert " + role + " column: " + name);
			result.add(column);
		}
		return result;
	}

	private static Set<String> names(final List<Column> columns) {
		final Set<String> result = new HashSet<>();
		columns.forEach(c -> result.add(c.getName()));
		return result;
	}

	private static final class UniqueKeyIterator implements Iterator<Row>, AutoCloseable {
		private final Iterator<Row> source;
		private final List<Column> keys;
		private final BulkUpsertDuplicateKeyStrategy strategy;
		private final BulkUpsertDuplicateRowSelector selector;
		private final Map<Key, Long> firstRows = new HashMap<>();
		private Iterator<Row> buffered;
		private boolean bufferedPrepared;
		private long rowNumber;
		private Row next;
		private boolean prepared;

		private UniqueKeyIterator(final Iterator<Row> source, final List<Column> keys,
				final BulkUpsertDuplicateKeyStrategy strategy,
				final BulkUpsertDuplicateRowSelector selector) {
			this.source = source;
			this.keys = keys;
			this.strategy = java.util.Objects.requireNonNull(strategy, "duplicateKeyStrategy");
			this.selector = selector;
			if (strategy == BulkUpsertDuplicateKeyStrategy.CUSTOM && selector == null)
				throw new IllegalArgumentException("duplicateRowSelector is required for CUSTOM duplicate keys");
		}

		@Override public boolean hasNext() {
			prepare();
			return next != null;
		}

		@Override public Row next() {
			prepare();
			if (next == null) throw new NoSuchElementException();
			final Row result = next;
			next = null;
			prepared = false;
			return result;
		}

		private void prepare() {
			if (prepared) return;
			prepared = true;
			if (strategy == BulkUpsertDuplicateKeyStrategy.KEEP_LAST
					|| strategy == BulkUpsertDuplicateKeyStrategy.CUSTOM) {
				prepareBuffered();
				if (buffered.hasNext()) next = buffered.next();
				return;
			}
			while (source.hasNext()) {
				final Row row = source.next();
				rowNumber++;
				final Object[] values = new Object[keys.size()];
				boolean hasNull = false;
				for (int i = 0; i < keys.size(); i++) {
					values[i] = row.get(keys.get(i));
					hasNull |= values[i] == null;
				}
				if (!hasNull) {
					final Long first = firstRows.putIfAbsent(new Key(values), rowNumber);
					if (first != null) {
						if (strategy == BulkUpsertDuplicateKeyStrategy.ERROR)
							throw new IllegalArgumentException("Duplicate bulk upsert key at source rows "
									+ first + " and " + rowNumber);
						continue;
					}
				}
				next = row;
				return;
			}
		}

		private void prepareBuffered() {
			if (bufferedPrepared) return;
			bufferedPrepared = true;
			final Map<Object, Row> retained = new LinkedHashMap<>();
			while (source.hasNext()) {
				final Row row = source.next();
				rowNumber++;
				final Object[] values = new Object[keys.size()];
				boolean hasNull = false;
				for (int i = 0; i < keys.size(); i++) {
					values[i] = row.get(keys.get(i));
					hasNull |= values[i] == null;
				}
				final Object key = hasNull ? new NullKey(rowNumber) : new Key(values);
				final Row current = retained.get(key);
				if (current == null) retained.put(key, row);
				else if (strategy == BulkUpsertDuplicateKeyStrategy.KEEP_LAST) retained.put(key, row);
				else {
					final Row selected = selector.select(current, row);
					if (selected != current && selected != row)
						throw new IllegalArgumentException("duplicateRowSelector must return retained or candidate at source row "
								+ rowNumber);
					retained.put(key, selected);
				}
			}
			buffered = retained.values().iterator();
		}

		@Override public void close() throws Exception {
			if (source instanceof AutoCloseable closeable) closeable.close();
		}
	}

	private record NullKey(long rowNumber) { }

	private static final class Key {
		private final Object[] values;
		private final int hash;
		private Key(final Object[] values) {
			this.values = values;
			this.hash = Arrays.deepHashCode(normalize(values));
		}
		@Override public int hashCode() { return hash; }
		@Override public boolean equals(final Object obj) {
			return obj instanceof Key other && Arrays.deepEquals(normalize(values), normalize(other.values));
		}
		private static Object[] normalize(final Object[] values) {
			final Object[] result = values.clone();
			for (int i = 0; i < result.length; i++) {
				final Object value = result[i];
				if (value != null && value.getClass().isArray() && !(value instanceof Object[])) {
					final int length = Array.getLength(value);
					final Object[] array = new Object[length];
					for (int j = 0; j < length; j++) array[j] = Array.get(value, j);
					result[i] = array;
				}
			}
			return result;
		}
	}
}
