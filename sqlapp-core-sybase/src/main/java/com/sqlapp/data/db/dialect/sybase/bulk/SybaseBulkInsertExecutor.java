/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.bulk;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** Sybase ASE streaming batch insert with identity-insert handling. */
public class SybaseBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	private final Dialect dialect;

	public SybaseBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
		this.dialect = dialect;
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		final boolean keepIdentity = options != null && options.isKeepIdentity();
		if (!keepIdentity) {
			return super.execute(connection, table, options);
		}
		final String name = dialect.getObjectFullName(table.getSchemaName(), table.getName());
		try (var statement = connection.createStatement()) {
			statement.execute("set identity_insert " + name + " on");
		}
		try {
			return super.execute(connection, table, options);
		} finally {
			try (var statement = connection.createStatement()) {
				statement.execute("set identity_insert " + name + " off");
			}
		}
	}
}
