/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.DbUtils;

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

	@Override
	public Iterator<Row> iterator(final String resumeToken) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final List<Object> values = resumeToken == null ? List.of()
				: codec.decode(keyColumns, resumeToken);
		final PreparedStatement statement = connection.prepareStatement(sql(dialect, !values.isEmpty()));
		try {
			statement.setFetchSize(fetchSize);
			if (!values.isEmpty()) {
				bind(statement, dialect, values);
			}
			return new KeysetIterator(statement, statement.executeQuery(), dialect);
		} catch (SQLException | RuntimeException e) {
			DbUtils.close(statement);
			throw e;
		}
	}

	@Override
	public String resumeToken(final Row row) {
		return codec.encode(keyColumns, row);
	}

	private String sql(final Dialect dialect, final boolean resumed) {
		final StringBuilder sql = new StringBuilder("SELECT ");
		for (int i = 0; i < table.getColumns().size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(dialect.quote(table.getColumns().get(i).getName()));
		}
		sql.append(" FROM ").append(dialect.getObjectFullName(table.getCatalogName(),
				table.getSchemaName(), table.getName()));
		if (resumed) {
			sql.append(" WHERE ");
			for (int i = 0; i < keyColumns.size(); i++) {
				if (i > 0) {
					sql.append(" OR ");
				}
				sql.append('(');
				for (int j = 0; j < i; j++) {
					sql.append(dialect.quote(keyColumns.get(j).getName())).append(" = ? AND ");
				}
				sql.append(dialect.quote(keyColumns.get(i).getName())).append(" > ?)");
			}
		}
		sql.append(" ORDER BY ");
		for (int i = 0; i < keyColumns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(dialect.quote(keyColumns.get(i).getName())).append(" ASC");
		}
		return sql.toString();
	}

	private void bind(final PreparedStatement statement, final Dialect dialect,
			final List<Object> values) throws SQLException {
		int parameter = 1;
		for (int i = 0; i < keyColumns.size(); i++) {
			for (int j = 0; j <= i; j++) {
				final DbDataType<?> type = dialect.getDbDataType(keyColumns.get(j));
				if (type == null) {
					throw new IllegalArgumentException("No JDBC type is available for keyset column: "
							+ keyColumns.get(j).getName());
				}
				type.getJdbcTypeHandler().setObject(statement, parameter++, values.get(j));
			}
		}
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
