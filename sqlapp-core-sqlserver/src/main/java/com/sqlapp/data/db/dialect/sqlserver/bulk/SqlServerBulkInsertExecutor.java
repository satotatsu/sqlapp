/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import java.sql.Connection;
import java.sql.SQLException;

import com.microsoft.sqlserver.jdbc.SQLServerBulkCopy;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;

/** SQL Server bulk insert backed by Microsoft JDBC SQLServerBulkCopy. */
public class SqlServerBulkInsertExecutor implements BulkInsertExecutor {
	private final Dialect dialect;

	public SqlServerBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		try (SQLServerBulkCopy bulkCopy = new SQLServerBulkCopy(connection);
				BulkData data = new BulkData(table, options)) {
			bulkCopy.setDestinationTableName(dialect.getObjectFullName(
					table.getCatalogName(), table.getSchemaName(), table.getName()));
			bulkCopy.setBulkCopyOptions(BulkData.toSqlServerOptions(options));
			for (final int ordinal : data.getColumnOrdinals()) {
				bulkCopy.addColumnMapping(ordinal, data.getColumnName(ordinal));
			}
			bulkCopy.writeToServer(data);
			return data.getRowCount();
		}
	}
}
