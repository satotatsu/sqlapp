/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/** Portable streaming bulk executor based on {@link PreparedStatement} batches. */
public class JdbcBatchBulkInsertExecutor implements BulkInsertExecutor {
	private static final int DEFAULT_BATCH_SIZE = 1_000;
	private final Dialect dialect;

	public JdbcBatchBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		final BulkOption effective = options == null ? BulkOption.defaults() : options;
		final List<Column> columns = writableColumns(table, effective);
		final int batchSize = effective.getBatchSize() == null
				? DEFAULT_BATCH_SIZE : effective.getBatchSize();
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be greater than zero");
		}
		final Iterator<Row> rows = table.getRows().iterator();
		Throwable failure = null;
		try (PreparedStatement statement = connection.prepareStatement(
				createInsertSql(table, columns))) {
			if (effective.getBulkCopyTimeout() != null) {
				statement.setQueryTimeout(effective.getBulkCopyTimeout());
			}
			long affected = 0;
			int pending = 0;
			while (rows.hasNext()) {
				bind(statement, rows.next(), columns);
				statement.addBatch();
				pending++;
				if (pending == batchSize) {
					affected += count(statement.executeBatch());
					pending = 0;
				}
			}
			if (pending > 0) {
				affected += count(statement.executeBatch());
			}
			return affected;
		} catch (SQLException | RuntimeException e) {
			failure = e;
			throw e;
		} finally {
			if (rows instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				} catch (Exception e) {
					if (failure != null) {
						failure.addSuppressed(e);
					} else {
						throw new SQLException("Failed to close JDBC batch rows", e);
					}
				}
			}
		}
	}

	protected List<Column> writableColumns(final Table table,
			final BulkOption options) {
		final List<Column> columns = new ArrayList<>();
		for (final Column column : table.getColumns()) {
			if (!column.isHidden() && CommonUtils.isEmpty(column.getFormula())
					&& (!column.isIdentity() || options.isKeepIdentity())) {
				columns.add(column);
			}
		}
		if (columns.isEmpty()) {
			throw new IllegalArgumentException(
					"No writable JDBC batch columns: " + table.getName());
		}
		return columns;
	}

	protected String createInsertSql(final Table table, final List<Column> columns) {
		final StringBuilder sql = new StringBuilder("INSERT INTO ")
				.append(dialect.getObjectFullName(table.getSchemaName(), table.getName()))
				.append(" (");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(dialect.quote(columns.get(i).getName()));
		}
		sql.append(") VALUES (");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append('?');
		}
		return sql.append(')').toString();
	}

	protected void bind(final PreparedStatement statement, final Row row,
			final List<Column> columns) throws SQLException {
		for (int i = 0; i < columns.size(); i++) {
			statement.setObject(i + 1, row.get(columns.get(i)));
		}
	}

	private long count(final int[] counts) throws SQLException {
		long affected = 0;
		for (final int value : counts) {
			if (value == Statement.EXECUTE_FAILED) {
				throw new SQLException("A JDBC bulk batch entry failed");
			}
			affected += value == Statement.SUCCESS_NO_INFO ? 1 : value;
		}
		return affected;
	}

	protected void validateOptions(final BulkOption options) {
		if (options != null && (options.isAllowEncryptedValueModifications()
				|| options.isCheckConstraints() || options.isFireTriggers()
				|| options.isTableLock() || options.isUseTransaction())) {
			throw new IllegalArgumentException(
					"JDBC batch does not support the requested vendor bulk options");
		}
	}
}
