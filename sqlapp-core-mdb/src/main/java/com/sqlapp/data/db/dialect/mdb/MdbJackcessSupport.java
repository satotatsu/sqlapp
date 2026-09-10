/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.CursorBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.IndexCursor;
import io.github.spannm.jackcess.PropertyMap;
import io.github.spannm.jackcess.RelationshipBuilder;
import io.github.spannm.jackcess.TableBuilder;

/**
 * Direct, high-throughput Jackcess operations for Access files.
 * <p>
 * A file may have only one session opened through this class in the current
 * JVM. Close every UCanAccess connection for the same file before opening a
 * session; Jackcess and UCanAccess must not write the file concurrently.
 * Operations write directly to the Access file and are not JDBC transactions.
 * </p>
 */
public final class MdbJackcessSupport implements AutoCloseable {
	private static final Set<Path> OPEN_FILES = ConcurrentHashMap.newKeySet();

	private final Path file;
	private final Database database;
	private boolean closed;
	private boolean dirty;

	/** Result of a primary-key upsert operation. */
	public record UpsertResult(int updated, int inserted) {
		public int total() {
			return updated + inserted;
		}
	}

	private MdbJackcessSupport(final Path file, final Database database) {
		this.file = file;
		this.database = database;
	}

	/** Opens an exclusive sqlapp Jackcess writer for an existing Access file. */
	public static MdbJackcessSupport open(final Path file) throws IOException {
		final Path normalized = normalize(file);
		if (!OPEN_FILES.add(normalized)) {
			throw new IllegalStateException(
					"An MDB Jackcess writer is already open for: " + normalized);
		}
		try {
			return new MdbJackcessSupport(normalized,
					new DatabaseBuilder().withPath(normalized).withReadOnly(false)
							.withAutoSync(false)
							.open());
		} catch (final IOException | RuntimeException e) {
			OPEN_FILES.remove(normalized);
			throw e;
		}
	}

	/**
	 * Adds rows in one Jackcess bulk call. AutoNumber values are written back to
	 * the supplied maps.
	 */
	public List<Map<String, Object>> insertRows(final String tableName,
			final List<? extends Map<String, Object>> rows) throws IOException {
		ensureOpen();
		if (rows.isEmpty()) {
			return List.of();
		}
		final io.github.spannm.jackcess.Table table = requireTable(tableName);
		final List<Object[]> values = new ArrayList<>(rows.size());
		for (final Map<String, Object> row : rows) {
			final Object[] value = new Object[table.getColumnCount()];
			for (final io.github.spannm.jackcess.Column column : table.getColumns()) {
				value[column.getColumnIndex()] = column.isAutoNumber()
						&& !containsKey(row, column.getName())
								? io.github.spannm.jackcess.Column.AUTO_NUMBER
								: get(row, column.getName());
			}
			values.add(value);
		}
		final List<? extends Object[]> inserted = table.addRows(values);
		dirty = true;
		final List<Map<String, Object>> result = new ArrayList<>(rows.size());
		for (int i = 0; i < inserted.size(); i++) {
			@SuppressWarnings("unchecked")
			final Map<String, Object> target = (Map<String, Object>) rows.get(i);
			final Object[] value = inserted.get(i);
			for (final io.github.spannm.jackcess.Column column : table.getColumns()) {
				if (column.isAutoNumber()) {
					target.put(column.getName(), value[column.getColumnIndex()]);
				}
			}
			result.add(target);
		}
		return result;
	}

	/** Updates rows through the table primary-key index, without a table scan. */
	public int updateRowsByPrimaryKey(final String tableName,
			final List<? extends Map<String, Object>> rows) throws IOException {
		ensureOpen();
		final io.github.spannm.jackcess.Table table = requireTable(tableName);
		final IndexCursor cursor = primaryKeyCursor(table);
		int count = 0;
		for (final Map<String, Object> row : sortedByPrimaryKey(table, rows)) {
			if (cursor.findFirstRowByEntry(primaryKeyValues(table, row))) {
				cursor.updateCurrentRowFromMap(new LinkedHashMap<>(row));
				count++;
			}
		}
		dirty |= count > 0;
		return count;
	}

	/** Deletes rows through the table primary-key index, without a table scan. */
	public int deleteRowsByPrimaryKey(final String tableName,
			final List<? extends Map<String, Object>> keys) throws IOException {
		ensureOpen();
		final io.github.spannm.jackcess.Table table = requireTable(tableName);
		final IndexCursor cursor = primaryKeyCursor(table);
		int count = 0;
		for (final Map<String, Object> key : sortedByPrimaryKey(table, keys)) {
			if (cursor.findFirstRowByEntry(primaryKeyValues(table, key))) {
				cursor.deleteCurrentRow();
				count++;
			}
		}
		dirty |= count > 0;
		return count;
	}

	/**
	 * Updates existing rows by primary key and inserts all missing rows in one
	 * bulk call. Rows without a complete primary key are inserts. AutoNumber
	 * values for inserted rows are written back to their maps.
	 */
	public UpsertResult upsertRowsByPrimaryKey(final String tableName,
			final List<? extends Map<String, Object>> rows) throws IOException {
		ensureOpen();
		final io.github.spannm.jackcess.Table table = requireTable(tableName);
		final IndexCursor cursor = primaryKeyCursor(table);
		final List<Map<String, Object>> inserts = new ArrayList<>();
		int updated = 0;
		for (final Map<String, Object> row : sortedByPrimaryKey(table, rows)) {
			final Object[] key = optionalPrimaryKeyValues(table, row);
			if (key != null && cursor.findFirstRowByEntry(key)) {
				cursor.updateCurrentRowFromMap(new LinkedHashMap<>(row));
				updated++;
			} else {
				inserts.add(row);
			}
		}
		if (updated > 0) {
			dirty = true;
		}
		if (!inserts.isEmpty()) {
			insertRows(tableName, inserts);
		}
		return new UpsertResult(updated, inserts.size());
	}

	/** Adds a column directly to an existing Access table. */
	public void addColumn(final String tableName, final Column column)
			throws IOException {
		toColumnBuilder(column).addToTable(requireTable(tableName));
		dirty = true;
	}

	/** Adds an index directly to an existing Access table. */
	public void addIndex(final String tableName, final Index index)
			throws IOException {
		toIndexBuilder(index).addToTable(requireTable(tableName));
		dirty = true;
	}

	/** Adds an Access relationship represented by a Schema foreign key. */
	public void addRelationship(final ForeignKeyConstraint foreignKey)
			throws IOException {
		ensureOpen();
		final String childName = foreignKey.getTable().getName();
		final RelationshipBuilder builder = new RelationshipBuilder(
				foreignKey.getRelatedTableName(), childName)
				.withName(foreignKey.getName()).withReferentialIntegrity();
		for (int i = 0; i < foreignKey.getColumns().size(); i++) {
			builder.addColumns(foreignKey.getRelatedColumns().get(i).getName(),
					foreignKey.getColumns().get(i).getName());
		}
		if (foreignKey.getUpdateRule() == CascadeRule.Cascade) {
			builder.withCascadeUpdates();
		}
		if (foreignKey.getDeleteRule() == CascadeRule.Cascade) {
			builder.withCascadeDeletes();
		}
		builder.toRelationship(database);
		dirty = true;
	}

	public void flush() throws IOException {
		ensureOpen();
		database.flush();
		dirty = false;
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			IOException failure = null;
			try {
				if (dirty) {
					database.flush();
				}
			} catch (final IOException e) {
				failure = e;
			} finally {
				try {
					database.close();
				} catch (final IOException e) {
					if (failure == null) {
						failure = e;
					} else {
						failure.addSuppressed(e);
					}
				} finally {
					OPEN_FILES.remove(file);
				}
			}
			if (failure != null) {
				throw failure;
			}
		}
	}

	/**
	 * Rebuilds an Access database into a new file using the target Schema.
	 * Existing target-column names are copied automatically. Optional mappings
	 * are target column name to source column name, grouped by target table.
	 */
	public static void rebuild(final Path sourceFile, final Path targetFile,
			final Schema targetSchema,
			final Map<String, ? extends Map<String, String>> columnMappings)
			throws IOException {
		final Path source = normalize(sourceFile);
		final Path target = normalize(targetFile);
		if (source.equals(target)) {
			throw new IllegalArgumentException(
					"Rebuild target must differ from the source file");
		}
		if (Files.exists(target)) {
			throw new IllegalArgumentException(
					"Rebuild target already exists: " + target);
		}
		if (!OPEN_FILES.add(source)) {
			throw new IllegalStateException(
					"An MDB Jackcess writer is already open for: " + source);
		}
		if (!OPEN_FILES.add(target)) {
			OPEN_FILES.remove(source);
			throw new IllegalStateException(
					"An MDB Jackcess writer is already open for: " + target);
		}
		try {
			try (Database input = new DatabaseBuilder().withPath(source)
					.withReadOnly(true).open();
					Database output = DatabaseBuilder.create(input.getFileFormat(),
							target.toFile())) {
				for (final Table model : targetSchema.getTables()) {
					createTable(output, model);
				}
				for (final Table model : targetSchema.getTables()) {
					copyRows(input.getTable(model.getName()),
							output.getTable(model.getName()),
							columnMappings == null ? null
									: columnMappings.get(model.getName()));
				}
				for (final Table model : targetSchema.getTables()) {
					for (final ForeignKeyConstraint fk : model.getConstraints()
							.getForeignKeyConstraints()) {
						addRelationship(output, fk);
					}
				}
			}
		} catch (final IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(target);
			} catch (final IOException cleanupError) {
				e.addSuppressed(cleanupError);
			}
			throw e;
		} finally {
			OPEN_FILES.remove(target);
			OPEN_FILES.remove(source);
		}
	}

	public static void rebuild(final Path sourceFile, final Path targetFile,
			final Schema targetSchema) throws IOException {
		rebuild(sourceFile, targetFile, targetSchema, Map.of());
	}

	private static void createTable(final Database database, final Table model)
			throws IOException {
		final TableBuilder builder = new TableBuilder(model.getName());
		for (final Column column : model.getColumns()) {
			builder.addColumn(toColumnBuilder(column));
		}
		final UniqueConstraint primaryKey = model.getPrimaryKeyConstraint();
		if (primaryKey != null) {
			builder.addIndex(toIndexBuilder(primaryKey));
		}
		for (final Index index : model.getIndexes()) {
			builder.addIndex(toIndexBuilder(index));
		}
		builder.toTable(database);
	}

	private static void copyRows(final io.github.spannm.jackcess.Table source,
			final io.github.spannm.jackcess.Table target,
			final Map<String, String> mapping) throws IOException {
		if (source == null) {
			return;
		}
		final List<Object[]> rows = new ArrayList<>();
		for (final io.github.spannm.jackcess.Row sourceRow : source) {
			final Object[] row = new Object[target.getColumnCount()];
			for (final io.github.spannm.jackcess.Column targetColumn : target
					.getColumns()) {
				final String sourceName = mapping != null
						&& mapping.containsKey(targetColumn.getName())
								? mapping.get(targetColumn.getName())
								: targetColumn.getName();
				if (source.getColumn(sourceName) != null) {
					row[targetColumn.getColumnIndex()] = sourceRow.get(sourceName);
				} else if (targetColumn.isAutoNumber()) {
					row[targetColumn.getColumnIndex()] = io.github.spannm.jackcess.Column.AUTO_NUMBER;
				}
			}
			rows.add(row);
		}
		if (!rows.isEmpty()) {
			target.setAllowAutoNumberInsert(true);
			target.addRows(rows);
		}
	}

	private static void addRelationship(final Database database,
			final ForeignKeyConstraint foreignKey) throws IOException {
		final RelationshipBuilder builder = new RelationshipBuilder(
				foreignKey.getRelatedTableName(), foreignKey.getTable().getName())
				.withName(foreignKey.getName()).withReferentialIntegrity();
		for (int i = 0; i < foreignKey.getColumns().size(); i++) {
			builder.addColumns(foreignKey.getRelatedColumns().get(i).getName(),
					foreignKey.getColumns().get(i).getName());
		}
		if (foreignKey.getUpdateRule() == CascadeRule.Cascade) {
			builder.withCascadeUpdates();
		}
		if (foreignKey.getDeleteRule() == CascadeRule.Cascade) {
			builder.withCascadeDeletes();
		}
		builder.toRelationship(database);
	}

	private static ColumnBuilder toColumnBuilder(final Column column) {
		final ColumnBuilder builder = new ColumnBuilder(column.getName(),
				toJackcessType(column));
		if (column.getLength() != null && column.getLength() > 0
				&& builder.getType().isTextual()
				&& !builder.getType().isLongValue()) {
			builder.withLengthInUnits(Math.toIntExact(column.getLength()));
		}
		if (column.getLength() != null && column.getLength() > 0
				&& builder.getType().getHasScalePrecision()) {
			builder.withPrecision(Math.toIntExact(column.getLength()));
		}
		if (column.getScale() != null
				&& builder.getType().getHasScalePrecision()) {
			builder.withScale(column.getScale());
		}
		builder.withAutoNumber(column.isIdentity());
		if (column.isNotNull()) {
			builder.withProperty(PropertyMap.REQUIRED_PROP, true);
		}
		if (column.getDefaultValue() != null) {
			builder.withProperty(PropertyMap.DEFAULT_VALUE_PROP,
					column.getDefaultValue());
		}
		if (column.getRemarks() != null) {
			builder.withProperty(PropertyMap.DESCRIPTION_PROP,
					column.getRemarks());
		}
		if (column.getCheck() != null) {
			builder.withProperty(PropertyMap.VALIDATION_RULE_PROP,
					column.getCheck());
		}
		return builder;
	}

	private static io.github.spannm.jackcess.DataType toJackcessType(
			final Column column) {
		if (column.getDataType() == null) {
			throw new IllegalArgumentException(
					"Column data type is required: " + column.getName());
		}
		return switch (column.getDataType().getJdbcType()) {
		case BOOLEAN, BIT -> io.github.spannm.jackcess.DataType.BOOLEAN;
		case TINYINT -> io.github.spannm.jackcess.DataType.BYTE;
		case SMALLINT -> io.github.spannm.jackcess.DataType.INT;
		case INTEGER -> io.github.spannm.jackcess.DataType.LONG;
		case BIGINT -> io.github.spannm.jackcess.DataType.BIG_INT;
		case REAL -> io.github.spannm.jackcess.DataType.FLOAT;
		case FLOAT, DOUBLE -> io.github.spannm.jackcess.DataType.DOUBLE;
		case DECIMAL, NUMERIC -> io.github.spannm.jackcess.DataType.NUMERIC;
		case DATE, TIME, TIME_WITH_TIMEZONE, TIMESTAMP,
				TIMESTAMP_WITH_TIMEZONE -> io.github.spannm.jackcess.DataType.SHORT_DATE_TIME;
		case BINARY, VARBINARY -> io.github.spannm.jackcess.DataType.BINARY;
		case LONGVARBINARY, BLOB -> io.github.spannm.jackcess.DataType.OLE;
		case CHAR, VARCHAR, NCHAR, NVARCHAR -> io.github.spannm.jackcess.DataType.TEXT;
		case LONGVARCHAR, LONGNVARCHAR, CLOB, NCLOB -> io.github.spannm.jackcess.DataType.MEMO;
		default -> throw new UnsupportedOperationException(
				"Jackcess cannot create Access column type "
						+ column.getDataType() + ": " + column.getName());
		};
	}

	private static IndexBuilder toIndexBuilder(final Index index) {
		final IndexBuilder builder = new IndexBuilder(index.getName());
		for (final ReferenceColumn column : index.getColumns()) {
			builder.withColumns(column.getOrder() != Order.Desc,
					column.getName());
		}
		if (index.isUnique()) {
			builder.withUnique();
		}
		if (Boolean.parseBoolean(index.getSpecifics().get(INDEX_IGNORE_NULLS))) {
			builder.withIgnoreNulls();
		}
		return builder;
	}

	private static IndexBuilder toIndexBuilder(
			final UniqueConstraint constraint) {
		final IndexBuilder builder = new IndexBuilder(constraint.getName());
		for (final ReferenceColumn column : constraint.getColumns()) {
			builder.withColumns(column.getName());
		}
		return constraint.isPrimaryKey() ? builder.withPrimaryKey()
				: builder.withUnique();
	}

	private static IndexCursor primaryKeyCursor(
			final io.github.spannm.jackcess.Table table) throws IOException {
		if (table.getPrimaryKeyIndex() == null) {
			throw new IllegalArgumentException(
					"Primary key is required for direct update/delete: "
							+ table.getName());
		}
		return CursorBuilder.createPrimaryKeyCursor(table);
	}

	private static Object[] primaryKeyValues(
			final io.github.spannm.jackcess.Table table,
			final Map<String, Object> row) {
		return table.getPrimaryKeyIndex().getColumns().stream()
				.map(column -> required(row, column.getName())).toArray();
	}

	private static Object[] optionalPrimaryKeyValues(
			final io.github.spannm.jackcess.Table table,
			final Map<String, Object> row) {
		final Object[] values = new Object[table.getPrimaryKeyIndex().getColumns()
				.size()];
		for (int i = 0; i < values.length; i++) {
			final String name = table.getPrimaryKeyIndex().getColumns().get(i)
					.getName();
			if (!containsKey(row, name) || get(row, name) == null) {
				return null;
			}
			values[i] = get(row, name);
		}
		return values;
	}

	private static List<Map<String, Object>> sortedByPrimaryKey(
			final io.github.spannm.jackcess.Table table,
			final List<? extends Map<String, Object>> rows) {
		final List<Map<String, Object>> sorted = new ArrayList<>(rows);
		sorted.sort((left, right) -> compareKeys(
				optionalPrimaryKeyValues(table, left),
				optionalPrimaryKeyValues(table, right)));
		return sorted;
	}

	private static int compareKeys(final Object[] left, final Object[] right) {
		if (left == null) {
			return right == null ? 0 : 1;
		}
		if (right == null) {
			return -1;
		}
		for (int i = 0; i < left.length; i++) {
			final int compared = compareValues(left[i], right[i]);
			if (compared != 0) {
				return compared;
			}
		}
		return 0;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static int compareValues(final Object left, final Object right) {
		if (left == right) {
			return 0;
		}
		if (left == null) {
			return -1;
		}
		if (right == null) {
			return 1;
		}
		if (left instanceof Number leftNumber
				&& right instanceof Number rightNumber) {
			return new BigDecimal(leftNumber.toString())
					.compareTo(new BigDecimal(rightNumber.toString()));
		}
		if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
			return Arrays.compareUnsigned(leftBytes, rightBytes);
		}
		if (left.getClass().isInstance(right) && left instanceof Comparable value) {
			return value.compareTo(right);
		}
		return Comparator.comparing(Object::toString,
				String.CASE_INSENSITIVE_ORDER).compare(left, right);
	}

	private io.github.spannm.jackcess.Table requireTable(final String name)
			throws IOException {
		ensureOpen();
		final io.github.spannm.jackcess.Table table = database.getTable(name);
		if (table == null) {
			throw new IllegalArgumentException("Access table not found: " + name);
		}
		return table;
	}

	private void ensureOpen() {
		if (closed) {
			throw new IllegalStateException("MDB Jackcess writer is closed");
		}
	}

	private static Path normalize(final Path path) {
		return path.toAbsolutePath().normalize();
	}

	private static boolean containsKey(final Map<String, ?> row,
			final String name) {
		return row.keySet().stream().anyMatch(name::equalsIgnoreCase);
	}

	private static Object get(final Map<String, ?> row, final String name) {
		return row.entrySet().stream()
				.filter(entry -> name.equalsIgnoreCase(entry.getKey()))
				.map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private static Object required(final Map<String, ?> row,
			final String name) {
		if (!containsKey(row, name)) {
			throw new IllegalArgumentException(
					"Primary-key value is required: " + name);
		}
		return get(row, name);
	}

	private static final String INDEX_IGNORE_NULLS = "IGNORE_NULLS";
}
