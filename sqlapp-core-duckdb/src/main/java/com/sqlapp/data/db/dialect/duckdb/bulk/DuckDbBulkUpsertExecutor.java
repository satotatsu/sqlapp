/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.jdbc.bulk.BulkUpsertTransaction;

/**
 * Bulk UPSERT for DuckDB with {@code INSERT ... ON CONFLICT (keys) DO UPDATE}.
 * This works on every supported DuckDB version (MERGE needs 1.4). The key
 * columns must match a primary key or unique constraint of the target table;
 * DuckDB rejects the statement otherwise.
 */
public class DuckDbBulkUpsertExecutor implements BulkUpsertExecutor {

	private final Dialect dialect;

	public DuckDbBulkUpsertExecutor(final Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkUpsertOption options)
			throws SQLException {
		Objects.requireNonNull(connection, "connection");
		Objects.requireNonNull(table, "table");
		final BulkUpsertOption option = options == null ? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, option);
		final List<Column> keys = plan.getKeyColumns();
		final List<Column> writable = plan.getStagingColumns();
		final List<Column> updates = plan.getUpdateColumns();
		final List<Column> bindings = new ArrayList<>();
		final String sql;
		if (option.isInsertWhenNotMatched()) {
			bindings.addAll(writable);
			sql = insertSql(table, writable, keys, updates, option.isUpdateWhenMatched());
		} else {
			bindings.addAll(updates);
			bindings.addAll(keys);
			sql = updateSql(table, updates, keys);
		}
		final DuckDbBulkInsertExecutor batch = new DuckDbBulkInsertExecutor(dialect) {
			@Override
			protected List<Column> writableColumns(final Table ignored, final BulkOption bulkOption) {
				return bindings;
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

	private String insertSql(final Table table, final List<Column> writable, final List<Column> keys,
			final List<Column> updates, final boolean update) {
		final StringBuilder sql = new StringBuilder("INSERT INTO ").append(name(table)).append(" (")
				.append(list(writable)).append(") VALUES (")
				.append(String.join(", ", java.util.Collections.nCopies(writable.size(), "?")))
				.append(") ON CONFLICT (").append(list(keys)).append(") DO ");
		if (!update || updates.isEmpty()) {
			return sql.append("NOTHING").toString();
		}
		sql.append("UPDATE SET ");
		for (int i = 0; i < updates.size(); i++) {
			final String column = dialect.quote(updates.get(i).getName());
			sql.append(i > 0 ? ", " : "").append(column).append(" = excluded.").append(column);
		}
		return sql.toString();
	}

	private String updateSql(final Table table, final List<Column> updates, final List<Column> keys) {
		final StringBuilder sql = new StringBuilder("UPDATE ").append(name(table)).append(" SET ");
		for (int i = 0; i < updates.size(); i++) {
			sql.append(i > 0 ? ", " : "").append(dialect.quote(updates.get(i).getName())).append(" = ?");
		}
		sql.append(" WHERE ");
		for (int i = 0; i < keys.size(); i++) {
			sql.append(i > 0 ? " AND " : "").append(dialect.quote(keys.get(i).getName())).append(" = ?");
		}
		return sql.toString();
	}

	private String name(final Table table) {
		return dialect.getObjectFullName(table.getSchemaName(), table.getName());
	}

	private String list(final List<Column> columns) {
		final StringBuilder sql = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			sql.append(i > 0 ? ", " : "").append(dialect.quote(columns.get(i).getName()));
		}
		return sql.toString();
	}
}
