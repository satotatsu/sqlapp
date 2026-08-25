/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresCopyFromBuilder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;

/** PostgreSQL bulk insert backed by JDBC CopyManager COPY FROM STDIN. */
public class PostgresBulkInsertExecutor implements BulkInsertExecutor {
	private final Dialect dialect;

	public PostgresBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		try (PostgresBulkDataReader reader = new PostgresBulkDataReader(table,
				options)) {
			final PostgresCopyFromBuilder builder = new PostgresCopyFromBuilder(
					dialect, table.getSchemaName(), table.getName()).format("csv");
			reader.getColumns().forEach(column -> builder.column(column.getName()));
			final CopyManager manager = new CopyManager(
					connection.unwrap(BaseConnection.class));
			return manager.copyIn(builder.build(), reader);
		} catch (IOException e) {
			throw new SQLException("Failed to stream PostgreSQL COPY rows for "
					+ table.getName(), e);
		}
	}

	private void validateOptions(final BulkOption options) {
		if (options == null) {
			return;
		}
		if (options.getBatchSize() != null
				|| options.getBulkCopyTimeout() != null
				|| options.isAllowEncryptedValueModifications()
				|| options.isFireTriggers() || options.isTableLock()
				|| options.isUseTransaction()) {
			throw new IllegalArgumentException(
					"PostgreSQL COPY does not support batchSize, bulkCopyTimeout, useTransaction, or SQL Server encrypted-value, fireTriggers and tableLock bulk options");
		}
	}
}
