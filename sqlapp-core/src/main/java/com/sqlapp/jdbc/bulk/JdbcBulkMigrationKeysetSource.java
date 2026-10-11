/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.RowComparisonOperator;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.JsonUtils;

/** Portable ascending JDBC keyset source for one or more unique key columns. */
public class JdbcBulkMigrationKeysetSource implements BulkMigrationKeysetSource {
	private final Connection connection;
	private final Table table;
	private final List<Column> keyColumns;
	private final BulkMigrationKeysetCodec codec;
	private final int fetchSize;

	/** Uses the table's complete primary key in declared order. */
	public JdbcBulkMigrationKeysetSource(final Connection connection, final Table table) {
		this(connection, table, primaryKeyNames(table));
	}

	/**
	 * Uses the specified columns in the given order. The columns must be non-null
	 * and exactly match a primary key, unique constraint, or unique index modeled
	 * on the table.
	 */
	public JdbcBulkMigrationKeysetSource(final Connection connection, final Table table,
			final List<String> keyColumnNames) {
		this(connection, table, keyColumnNames, new DefaultBulkMigrationKeysetCodec(), 10_000);
	}

	public JdbcBulkMigrationKeysetSource(final Connection connection, final Table table,
			final String... keyColumnNames) {
		this(connection, table, List.of(keyColumnNames));
	}

	public JdbcBulkMigrationKeysetSource(final Connection connection, final Table table,
			final List<String> keyColumnNames, final BulkMigrationKeysetCodec codec,
			final int fetchSize) {
		this.connection = Objects.requireNonNull(connection, "connection");
		this.table = Objects.requireNonNull(table, "table");
		this.codec = Objects.requireNonNull(codec, "codec");
		if (keyColumnNames == null || keyColumnNames.isEmpty()) {
			throw new IllegalArgumentException("At least one keyset column is required");
		}
		if (fetchSize <= 0) {
			throw new IllegalArgumentException("fetchSize must be greater than zero");
		}
		this.fetchSize = fetchSize;
		final List<Column> resolved = new ArrayList<>(keyColumnNames.size());
		for (final String name : keyColumnNames) {
			final Column column = table.getColumns().get(name);
			if (column == null) {
				throw new IllegalArgumentException("Unknown keyset column: " + name);
			}
			if (resolved.contains(column)) {
				throw new IllegalArgumentException("Duplicate keyset column: " + name);
			}
			resolved.add(column);
		}
		this.keyColumns = List.copyOf(resolved);
		validateKeyColumns(table, this.keyColumns);
	}

	private static void validateKeyColumns(final Table table, final List<Column> columns) {
		for (final Column column : columns) {
			if (!column.isNotNull()) {
				throw new IllegalArgumentException("Keyset column must be NOT NULL: "
						+ column.getName());
			}
		}
		final boolean uniqueConstraint = table.getConstraints().getUniqueConstraints().stream()
				.anyMatch(constraint -> matchesKeyColumns(table, constraint.getColumns(), columns));
		final boolean uniqueIndex = table.getIndexes().stream().filter(index -> index.isUnique())
				.anyMatch(index -> matchesKeyColumns(table, index.getColumns().stream()
						.filter(column -> !column.isIncludedColumn()).toList(), columns));
		if (!uniqueConstraint && !uniqueIndex) {
			throw new IllegalArgumentException(
					"Keyset columns must exactly match a primary key, unique constraint, "
							+ "or unique index: "
							+ columns.stream().map(Column::getName).toList());
		}
	}

	private static boolean matchesKeyColumns(final Table table,
			final Collection<? extends ReferenceColumn> references,
			final List<Column> columns) {
		if (references.size() != columns.size()) {
			return false;
		}
		return references.stream().allMatch(reference -> {
			final Column resolved = reference.getColumn();
			if (resolved != null && columns.contains(resolved)) {
				return true;
			}
			return columns.stream().anyMatch(column -> table.getColumns().isCaseSensitive()
					? Objects.equals(column.getName(), reference.getName())
					: column.getName().equalsIgnoreCase(reference.getName()));
		});
	}

	private static List<String> primaryKeyNames(final Table table) {
		Objects.requireNonNull(table, "table");
		if (table.getPrimaryKeyConstraint() == null
				|| table.getPrimaryKeyConstraint().getColumns().isEmpty()) {
			throw new IllegalArgumentException("The table has no primary key; specify unique keyset columns");
		}
		return table.getPrimaryKeyConstraint().getColumns().stream()
				.map(column -> column.getName()).toList();
	}

	@Override
	public Table getTable() {
		return table;
	}

	/** Ordered unique columns used by this source. */
	public List<String> getKeyColumnNames() {
		return keyColumns.stream().map(Column::getName).toList();
	}

	@Override
	public String getConfigurationFingerprint() {
		final String codecFingerprint = codec.getConfigurationFingerprint();
		if (codecFingerprint == null || codecFingerprint.isBlank()) {
			throw new IllegalStateException(
					"Keyset codec configuration fingerprint must not be blank");
		}
		return JsonUtils.toJsonString(List.of(
				keyColumns.stream().map(Column::getName).toList(),
				codecFingerprint, fetchSize));
	}

	@Override
	public Iterator<Row> iterator(final String resumeToken) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final List<Object> values = decode(resumeToken);
		final Table queryTable = queryTable();
		final TableRelation relation = new TableRelation(queryTable);
		final SqlNode node = selectNode(dialect, relation);
		final var parameters = values.isEmpty() ? node.eval(relation)
				: node.eval(relation, resumeRow(queryTable, values));
		final PreparedStatement statement = parameters.createStatement(connection);
		try {
			statement.setFetchSize(fetchSize);
			parameters.setBind(statement);
			return new KeysetIterator(statement, statement.executeQuery(), dialect);
		} catch (SQLException | RuntimeException e) {
			DbUtils.close(statement);
			throw e;
		}
	}

	private List<Object> decode(final String resumeToken) {
		if (resumeToken == null) {
			return List.of();
		}
		final List<Object> values = codec.decode(keyColumns, resumeToken);
		if (values == null) {
			throw new IllegalArgumentException("Keyset codec returned null values");
		}
		if (values.size() != keyColumns.size()) {
			throw new IllegalArgumentException("Keyset codec returned " + values.size()
					+ " values but " + keyColumns.size() + " key columns are configured");
		}
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) == null) {
				throw new IllegalArgumentException(
						"Keyset codec returned a null value at index " + i);
			}
		}
		return List.copyOf(values);
	}

	@Override
	public String resumeToken(final Row row) {
		return codec.encode(keyColumns, row);
	}

	private Table queryTable() {
		final Table query = new Table(table.getName()).setCatalogName(table.getCatalogName())
				.setSchemaName(table.getSchemaName());
		for (final Column column : table.getColumns()) {
			query.getColumns().add(column.clone());
		}
		query.setPrimaryKey((String) null, keyColumns.stream()
				.map(column -> query.getColumns().get(column.getName())).toArray(Column[]::new));
		return query;
	}

	private SqlNode selectNode(final Dialect dialect, final TableRelation relation) {
		final var registry = dialect.createSqlFactoryRegistry();
		return registry.getTableOptions().useSelectByRowComparisonOperatorStrategy(
				table -> RowComparisonOperator.GREATER_THAN,
				() -> registry.createSqlNodes(relation, SqlType.SELECT_BY_ROW).get(0));
	}

	private Row resumeRow(final Table queryTable, final List<Object> values) {
		final Row row = queryTable.newRow();
		for (int i = 0; i < keyColumns.size(); i++) {
			row.put(keyColumns.get(i).getName(), values.get(i));
		}
		return row;
	}

	private final class KeysetIterator implements Iterator<Row>, AutoCloseable {
		private final PreparedStatement statement;
		private ResultSet resultSet;
		private final List<DbDataType<?>> types;
		private Boolean available;

		private KeysetIterator(final PreparedStatement statement, final ResultSet resultSet,
				final Dialect dialect) {
			this.statement = statement;
			this.resultSet = resultSet;
			final List<DbDataType<?>> resolvedTypes = new ArrayList<>(table.getColumns().size());
			for (final Column column : table.getColumns()) {
				final DbDataType<?> type = dialect.getDbDataType(column);
				if (type == null) {
					throw new IllegalArgumentException("No JDBC type is available for column: "
							+ column.getName());
				}
				resolvedTypes.add(type);
			}
			this.types = List.copyOf(resolvedTypes);
		}

		@Override
		public boolean hasNext() {
			if (available != null) {
				return available;
			}
			try {
				available = resultSet.next();
				if (!available) {
					close();
				}
				return available;
			} catch (SQLException e) {
				close();
				throw new IllegalStateException("Failed to read keyset source", e);
			}
		}

		@Override
		public Row next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			available = null;
			final Row row = table.newRow();
			try {
				for (int i = 0; i < types.size(); i++) {
					row.put(i, types.get(i).getJdbcTypeHandler().getObject(resultSet, i + 1));
				}
				return row;
			} catch (SQLException e) {
				close();
				throw new IllegalStateException("Failed to map keyset source row", e);
			}
		}

		@Override
		public void close() {
			DbUtils.close(resultSet);
			DbUtils.close(statement);
			resultSet = null;
		}
	}
}
