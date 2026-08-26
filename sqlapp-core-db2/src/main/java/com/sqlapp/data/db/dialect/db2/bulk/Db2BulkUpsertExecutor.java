/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.util.CommonUtils;

/** DB2 bulk upsert using a declared global temporary table and MERGE. */
public class Db2BulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public Db2BulkUpsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkUpsertOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		final BulkUpsertOption option = options == null ? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, option);
		final List<Column> keys = plan.getKeyColumns();
		final List<Column> staged = plan.getStagingColumns();
		final List<Column> updates = plan.getUpdateColumns();
		final String stage = stagingName(option);
		final String stageSql = "SESSION." + quote(stage);
		final String target = dialect.getObjectFullName(table.getCatalogName(),
				table.getSchemaName(), table.getName());
		final boolean manageTransaction = option.isUseTransaction() && connection.getAutoCommit();
		boolean created = false;
		Throwable failure = null;
		SQLException cleanupFailure = null;
		try {
			if (manageTransaction) {
				connection.setAutoCommit(false);
			}
			try (var statement = connection.createStatement()) {
				statement.execute("DECLARE GLOBAL TEMPORARY TABLE " + stageSql
						+ " AS (SELECT " + columnList(staged, null) + " FROM " + target
						+ " WHERE 1 = 0) WITH NO DATA ON COMMIT PRESERVE ROWS NOT LOGGED");
				created = true;
			}
			BulkInsertResolver.resolve(dialect).execute(connection,
					plan.createStagingTable(stage), stagingBulkOption(option.getBulkOption()));
			final long affected;
			try (var statement = connection.createStatement()) {
				affected = statement.executeUpdate(mergeSql(target, stageSql, keys,
						staged, updates, option));
			}
			if (manageTransaction) {
				connection.commit();
			}
			return affected;
		} catch (SQLException | RuntimeException e) {
			failure = e;
			if (manageTransaction) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					e.addSuppressed(rollbackFailure);
				}
			}
			throw e;
		} finally {
			if (created) {
				try (var statement = connection.createStatement()) {
					statement.execute("DROP TABLE " + stageSql);
				} catch (SQLException e) {
					if (failure != null) {
						failure.addSuppressed(e);
					} else {
						cleanupFailure = e;
					}
				}
			}
			if (manageTransaction) {
				try {
					connection.setAutoCommit(true);
				} catch (SQLException e) {
					if (failure != null) {
						failure.addSuppressed(e);
					} else if (cleanupFailure != null) {
						cleanupFailure.addSuppressed(e);
					} else {
						throw e;
					}
				}
			}
			if (failure == null && cleanupFailure != null) {
				throw cleanupFailure;
			}
		}
	}

	private String mergeSql(final String target, final String stage,
			final List<Column> keys, final List<Column> staged,
			final List<Column> updates, final BulkUpsertOption option) {
		final StringBuilder sql = new StringBuilder("MERGE INTO ").append(target)
				.append(" AS target USING ").append(stage).append(" AS source ON ");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) sql.append(" AND ");
			final String name = quote(keys.get(i).getName());
			sql.append("target.").append(name).append(" = source.").append(name);
		}
		if (option.isUpdateWhenMatched() && !updates.isEmpty()) {
			sql.append(" WHEN MATCHED THEN UPDATE SET ");
			for (int i = 0; i < updates.size(); i++) {
				if (i > 0) sql.append(", ");
				final String name = quote(updates.get(i).getName());
				sql.append("target.").append(name).append(" = source.").append(name);
			}
		}
		if (option.isInsertWhenNotMatched()) {
			sql.append(" WHEN NOT MATCHED THEN INSERT (").append(columnList(staged, null))
					.append(") VALUES (").append(columnList(staged, "source")).append(')');
		}
		return sql.toString();
	}

	private BulkOption stagingBulkOption(final BulkOption source) {
		final BulkOption option = source == null ? BulkOption.defaults() : source;
		return BulkOption.builder().batchSize(option.getBatchSize())
				.bulkCopyTimeout(option.getBulkCopyTimeout()).keepIdentity(true)
				.keepNulls(true).build();
	}

	private String stagingName(final BulkUpsertOption option) {
		final String name = CommonUtils.isEmpty(option.getStagingTableName())
				? "SQLAPP_UP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
				: option.getStagingTableName();
		if (!name.matches("[A-Za-z][A-Za-z0-9_$#]{0,127}")) {
			throw new IllegalArgumentException("Invalid DB2 stagingTableName: " + name);
		}
		return name;
	}

	private Set<String> names(final List<Column> columns) {
		final Set<String> result = new HashSet<>();
		columns.forEach(column -> result.add(column.getName()));
		return result;
	}

	private String columnList(final List<Column> columns, final String alias) {
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) result.append(", ");
			if (alias != null) result.append(alias).append('.');
			result.append(quote(columns.get(i).getName()));
		}
		return result.toString();
	}

	private String quote(final String name) {
		return dialect.quote(name);
	}
}
