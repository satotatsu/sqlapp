/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.jdbc.bulk.BulkUpsertTransaction;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** Streaming HSQLDB 2.x MERGE executor using JDBC batches. */
public class HsqlBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public HsqlBulkUpsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkUpsertOption options)
			throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		final BulkUpsertOption option = options == null ? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, option);
		final List<Column> staged = plan.getStagingColumns();
		final String sql = mergeSql(table, staged, plan.getKeyColumns(), plan.getUpdateColumns(), option);
		final JdbcBatchBulkInsertExecutor batch = new JdbcBatchBulkInsertExecutor(dialect) {
			@Override
			protected List<Column> writableColumns(final Table ignored, final BulkOption bulkOption) {
				return staged;
			}

			@Override
			protected String createInsertSql(final Table ignored, final List<Column> columns) {
				return sql;
			}
		};
		try (var transaction = BulkUpsertTransaction.begin(connection, option.isUseTransaction())) {
			try {
				final long affected = batch.execute(connection, plan.createStagingTable(table.getName()),
						option.getBulkOption());
				transaction.commit();
				return affected;
			} catch (SQLException | RuntimeException e) {
				transaction.rollback(e);
				throw e;
			}
		}
	}

	private String mergeSql(final Table table, final List<Column> staged, final List<Column> keys,
			final List<Column> updates, final BulkUpsertOption option) {
		final StringBuilder sql = new StringBuilder("MERGE INTO ").append(name(table))
				.append(" AS target USING (VALUES (").append(params(staged.size())).append(")) AS source (")
				.append(list(staged, null)).append(") ON (");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			final String column = quote(keys.get(i).getName());
			sql.append("target.").append(column).append(" = source.").append(column);
		}
		sql.append(')');
		if (option.isUpdateWhenMatched() && !updates.isEmpty()) {
			sql.append(" WHEN MATCHED THEN UPDATE SET ");
			for (int i = 0; i < updates.size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				final String column = quote(updates.get(i).getName());
				sql.append("target.").append(column).append(" = source.").append(column);
			}
		}
		if (option.isInsertWhenNotMatched()) {
			sql.append(" WHEN NOT MATCHED THEN INSERT (").append(list(staged, null)).append(") VALUES (")
					.append(list(staged, "source")).append(')');
		}
		return sql.toString();
	}

	private String name(final Table table) {
		return dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName());
	}

	private String list(final List<Column> columns, final String alias) {
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				result.append(", ");
			}
			if (alias != null) {
				result.append(alias).append('.');
			}
			result.append(quote(columns.get(i).getName()));
		}
		return result.toString();
	}

	private static String params(final int count) {
		return String.join(", ", java.util.Collections.nCopies(count, "?"));
	}

	private String quote(final String name) {
		return dialect.quote(name);
	}
}
