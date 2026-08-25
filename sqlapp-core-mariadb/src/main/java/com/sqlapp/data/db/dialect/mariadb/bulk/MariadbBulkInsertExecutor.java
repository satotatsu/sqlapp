/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.bulk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.bulk.MySqlBulkDataInputStream;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;

/** MariaDB bulk insert backed by Connector/J LOAD DATA LOCAL INFILE. */
public class MariadbBulkInsertExecutor implements BulkInsertExecutor {
	private final Dialect dialect;

	public MariadbBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		try (MySqlBulkDataInputStream input = new MySqlBulkDataInputStream(table, options);
				var statement = connection.createStatement()) {
			final org.mariadb.jdbc.Statement mariadbStatement =
					statement.unwrap(org.mariadb.jdbc.Statement.class);
			final StringBuilder sql = new StringBuilder("LOAD DATA LOCAL INFILE "
					+ "'sqlapp-stream' INTO TABLE ")
					.append(dialect.getObjectFullName(table.getSchemaName(), table.getName()))
					.append(" CHARACTER SET utf8mb4 FIELDS TERMINATED BY X'1F' "
							+ "ESCAPED BY '\\\\' LINES TERMINATED BY X'1E' (");
			for (int i = 0; i < input.getColumns().size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				final var column = input.getColumns().get(i);
				if (column.getDataType() != null && column.getDataType().isBinary()) {
					sql.append("@sqlapp_bulk_").append(i);
				} else {
					sql.append(dialect.quote(column.getName()));
				}
			}
			sql.append(')');
			boolean firstBinary = true;
			for (int i = 0; i < input.getColumns().size(); i++) {
				final var column = input.getColumns().get(i);
				if (column.getDataType() != null && column.getDataType().isBinary()) {
					sql.append(firstBinary ? " SET " : ", ")
							.append(dialect.quote(column.getName()))
							.append("=UNHEX(@sqlapp_bulk_").append(i).append(')');
					firstBinary = false;
				}
			}
			mariadbStatement.setLocalInfileInputStream(input);
			try {
				return statement.executeUpdate(sql.toString());
			} finally {
				mariadbStatement.setLocalInfileInputStream(null);
			}
		} catch (IOException e) {
			throw new SQLException("Failed to stream MariaDB bulk rows for "
					+ table.getName(), e);
		}
	}

	private void validateOptions(final BulkOption options) {
		if (options != null && (options.getBatchSize() != null
				|| options.getBulkCopyTimeout() != null
				|| options.isAllowEncryptedValueModifications()
				|| options.isCheckConstraints() || options.isFireTriggers()
				|| options.isKeepNulls() || options.isTableLock()
				|| options.isUseTransaction())) {
			throw new IllegalArgumentException(
					"MariaDB LOAD DATA does not support the requested bulk options");
		}
	}
}
