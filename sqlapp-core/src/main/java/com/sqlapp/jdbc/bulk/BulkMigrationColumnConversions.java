/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonUtils;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.eval.mvel.MvelCompiledEvaluator;
import com.sqlapp.util.eval.mvel.SqlappParserContextFactory;

/**
 * Projects rows read from a bulk migration source into a logical source table
 * whose column types may differ, optionally converting values with per-column
 * MVEL expressions.
 *
 * <p>The source table describes how rows are read. The logical table has the same
 * columns in the same order and carries the types used for copying, checkpoint
 * hashes, verification and repair. Each expression is evaluated with
 * {@code value} (the column's source value), {@code column} (the source column
 * name) and {@code row} (a read-only map of every unconverted source value by
 * source column name). The result, or the unconverted value of a column without an
 * expression, is stored through the logical column's type conversion.</p>
 *
 * <p>Expressions must be deterministic: verification and repair evaluate them
 * again and compare the result with the copied target rows. Expressions run with
 * the privileges of the migration process, so treat files containing them as
 * executable code.</p>
 */
public final class BulkMigrationColumnConversions {
	private final Table source;
	private final Table logical;
	private final List<Column> converted;
	private final List<String> expressions;
	private final List<MvelCompiledEvaluator> evaluators;
	private final List<Column> retyped;
	private final String fingerprint;

	private BulkMigrationColumnConversions(final Table source, final Table logical, final List<Column> converted,
			final List<String> expressions, final List<MvelCompiledEvaluator> evaluators, final List<Column> retyped) {
		this.source = source;
		this.logical = logical;
		this.converted = List.copyOf(converted);
		this.expressions = List.copyOf(expressions);
		this.evaluators = List.copyOf(evaluators);
		this.retyped = List.copyOf(retyped);
		this.fingerprint = isEmpty() ? null : fingerprint();
	}

	/** Expressions only: rows keep the source table's types. */
	public static BulkMigrationColumnConversions mvel(final Table table, final Map<String, String> expressions) {
		return of(table, table, expressions);
	}

	/**
	 * Compiles conversions keyed by source column name. Unknown, duplicate or blank
	 * entries and invalid expressions are rejected before any row is read.
	 *
	 * @param source      table rows are read from
	 * @param logical     table with the same column names and order whose types the
	 *                    projected rows use; may be {@code source}
	 * @param expressions MVEL expression by source column name; may be null or
	 *                    empty
	 */
	public static BulkMigrationColumnConversions of(final Table source, final Table logical,
			final Map<String, String> expressions) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(logical, "logical");
		if (source.getColumns().size() != logical.getColumns().size()) {
			throw new IllegalArgumentException("Logical table must have the source columns: " + source.getName());
		}
		final List<Column> retyped = new ArrayList<>();
		for (int i = 0; i < source.getColumns().size(); i++) {
			final Column from = source.getColumns().get(i);
			final Column to = logical.getColumns().get(i);
			if (!from.getName().equals(to.getName())) {
				throw new IllegalArgumentException("Logical table must keep source column order: " + source.getName()
						+ "." + from.getName());
			}
			if (!type(from).equals(type(to))) {
				retyped.add(to);
			}
		}
		final Map<Column, String> resolved = new HashMap<>();
		if (expressions != null) {
			for (final Map.Entry<String, String> entry : expressions.entrySet()) {
				final Column column = entry.getKey() == null ? null : source.getColumns().get(entry.getKey());
				if (column == null) {
					throw new IllegalArgumentException(
							"Unknown column conversion source column: " + source.getName() + "." + entry.getKey());
				}
				if (entry.getValue() == null || entry.getValue().isBlank()) {
					throw new IllegalArgumentException("Column conversion expression must not be blank: "
							+ source.getName() + "." + column.getName());
				}
				if (resolved.put(column, entry.getValue()) != null) {
					throw new IllegalArgumentException(
							"Duplicate column conversion source column: " + source.getName() + "." + column.getName());
				}
			}
		}
		final List<Column> converted = new ArrayList<>();
		final List<String> texts = new ArrayList<>();
		final List<MvelCompiledEvaluator> evaluators = new ArrayList<>();
		// Table column order keeps evaluation and fingerprints independent of map order.
		for (final Column column : source.getColumns()) {
			final String expression = resolved.get(column);
			if (expression == null) {
				continue;
			}
			final MvelCompiledEvaluator evaluator;
			try {
				evaluator = new MvelCompiledEvaluator(expression,
						SqlappParserContextFactory.getInstance().getParserContext());
			} catch (final RuntimeException e) {
				throw new IllegalArgumentException("Invalid column conversion expression for " + source.getName()
						+ "." + column.getName() + ": " + e.getMessage(), e);
			}
			converted.add(column);
			texts.add(expression);
			evaluators.add(evaluator);
		}
		return new BulkMigrationColumnConversions(source, logical, converted, texts, evaluators, retyped);
	}

	/** True when rows pass through unchanged. */
	public boolean isEmpty() {
		return converted.isEmpty() && retyped.isEmpty();
	}

	/** Table rows are read from. */
	public Table getSourceTable() {
		return source;
	}

	/** Table whose types projected rows use. */
	public Table getTable() {
		return logical;
	}

	/** Converted source column names and expressions in table column order. */
	public Map<String, String> getExpressions() {
		final Map<String, String> result = new LinkedHashMap<>();
		for (int i = 0; i < converted.size(); i++) {
			result.put(converted.get(i).getName(), expressions.get(i));
		}
		return Collections.unmodifiableMap(result);
	}

	/**
	 * Stable SHA-256 identity of the expressions and changed column types, or
	 * {@code null} when empty.
	 */
	public String getFingerprint() {
		return fingerprint;
	}

	/**
	 * Rejects expressions and type changes on columns whose source values identify
	 * rows, such as primary, keyset or UPSERT key columns.
	 */
	public void requireUnconverted(final Collection<String> columnNames, final String role) {
		if (columnNames == null || isEmpty()) {
			return;
		}
		final var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(columnNames);
		for (final Column column : converted) {
			if (names.contains(column.getName())) {
				throw new IllegalArgumentException("Column conversions cannot change " + role + " column "
						+ source.getName() + "." + column.getName()
						+ ". Keys identify rows for resume, verification and repair.");
			}
		}
		for (final Column column : retyped) {
			if (names.contains(column.getName())) {
				throw new IllegalArgumentException("Copy types cannot change " + role + " column " + source.getName()
						+ "." + column.getName() + ". Keys are compared with their source type.");
			}
		}
	}

	/** Projects one source row into a new logical row. */
	public Row apply(final Row row) {
		if (row == null) {
			return null;
		}
		if (isEmpty() && row.getTable() == logical) {
			return row;
		}
		final Map<String, Object> raw = new LinkedHashMap<>();
		for (final Column column : source.getColumns()) {
			raw.put(column.getName(), row.get(column.getName()));
		}
		final Map<String, Object> values = Collections.unmodifiableMap(raw);
		final Map<String, Object> projected = new HashMap<>(raw);
		for (int i = 0; i < converted.size(); i++) {
			final Column column = converted.get(i);
			final Map<String, Object> variables = new HashMap<>();
			variables.put("value", values.get(column.getName()));
			variables.put("column", column.getName());
			variables.put("row", values);
			try {
				projected.put(column.getName(), evaluators.get(i).eval(variables));
			} catch (final RuntimeException e) {
				throw failure(column, e);
			}
		}
		final Row result = logical.newRow();
		for (final Column column : logical.getColumns()) {
			final Object value = projected.get(column.getName());
			try {
				result.put(column.getName(), value);
			} catch (final RuntimeException e) {
				throw failure(column, e);
			}
			// Some converters return null for unparseable text; never drop a value silently.
			if (value != null && !(value instanceof CharSequence text && text.isEmpty())
					&& result.get(column.getName()) == null) {
				throw new IllegalStateException("Column conversion failed for " + source.getName() + "."
						+ column.getName() + ": the value cannot be represented as " + column.getDataType());
			}
		}
		return result;
	}

	/** Returns an iterator that projects rows as they are read and keeps the delegate closeable. */
	public Iterator<Row> iterator(final Iterator<Row> rows) {
		Objects.requireNonNull(rows, "rows");
		if (isEmpty() && source == logical) {
			return rows;
		}
		return new ConvertingIterator(rows);
	}

	/**
	 * Row handler for the logical table that reads the source table, such as a
	 * full-scan JDBC reader, and projects its rows.
	 *
	 * @param reader lazy reader of the source table
	 */
	public RowIteratorHandler rowIteratorHandler(final RowIteratorHandler reader) {
		Objects.requireNonNull(reader, "reader");
		if (isEmpty() && source == logical) {
			return reader;
		}
		return new RowIteratorHandler() {
			@Override
			public Iterator<Row> iterator(final RowCollection rows) {
				// The reader builds its query from the source table, not the logical one.
				return BulkMigrationColumnConversions.this.iterator(reader.iterator(source.getRows()));
			}
		};
	}

	private IllegalStateException failure(final Column column, final RuntimeException e) {
		// Values are omitted: they may contain personal or secret data.
		return new IllegalStateException("Column conversion failed for " + source.getName() + "." + column.getName()
				+ " (" + e.getClass().getSimpleName() + ")", e);
	}

	private static List<Object> type(final Column column) {
		return java.util.Arrays.asList(column.getDataType() == null ? null : column.getDataType().name(),
				column.getLength(), column.getScale());
	}

	private String fingerprint() {
		final List<List<Object>> values = new ArrayList<>();
		for (final Column column : source.getColumns()) {
			final int index = converted.indexOf(column);
			final Column logicalColumn = logical.getColumns().get(column.getName());
			final boolean changedType = retyped.contains(logicalColumn);
			if (index < 0 && !changedType) {
				continue;
			}
			values.add(java.util.Arrays.asList(column.getName(), index < 0 ? null : expressions.get(index),
					changedType ? type(logicalColumn) : null));
		}
		final String text = JsonUtils.toJsonString(List.of("MVEL_COLUMN_CONVERSIONS_V2", values));
		return "sha256:" + MessageDigests.SHA256.checksumAsString(text.getBytes(StandardCharsets.UTF_8))
				.toLowerCase(Locale.ROOT);
	}

	private final class ConvertingIterator implements Iterator<Row>, AutoCloseable {
		private final Iterator<Row> delegate;

		private ConvertingIterator(final Iterator<Row> delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean hasNext() {
			return delegate.hasNext();
		}

		@Override
		public Row next() {
			final Row row = delegate.next();
			try {
				return apply(row);
			} catch (final RuntimeException e) {
				try {
					close();
				} catch (final Exception closeFailure) {
					e.addSuppressed(closeFailure);
				}
				throw e;
			}
		}

		@Override
		public void close() throws Exception {
			if (delegate instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}
}
