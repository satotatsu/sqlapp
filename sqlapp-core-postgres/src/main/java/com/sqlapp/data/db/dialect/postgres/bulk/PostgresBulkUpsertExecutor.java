/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

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

/** PostgreSQL bulk upsert using COPY, a temp table and ON CONFLICT. */
public class PostgresBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public PostgresBulkUpsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkUpsertOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		final BulkUpsertOption effective = options == null
				? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, effective);
		final List<Column> keys = plan.getKeyColumns();
		final List<Column> stagingColumns = plan.getStagingColumns();
		final List<Column> updateColumns = plan.getUpdateColumns();
		final String stagingName = stagingName(effective);
		final String targetName = dialect.getObjectFullName(table.getCatalogName(),
				table.getSchemaName(), table.getName());
		final boolean manageTransaction = effective.isUseTransaction()
				&& connection.getAutoCommit();
		boolean stagingCreated = false;
		Throwable failure = null;
		SQLException cleanupFailure = null;
		try {
			if (manageTransaction) {
				connection.setAutoCommit(false);
			}
			try (var statement = connection.createStatement()) {
				statement.execute(createStagingSql(targetName, stagingName,
						stagingColumns));
				stagingCreated = true;
			}
			BulkInsertResolver.resolve(dialect).execute(connection,
					plan.createStagingTable(stagingName),
					stagingBulkOption(effective.getBulkOption()));
			final long affected = apply(connection, targetName, stagingName,
					keys, stagingColumns, updateColumns, effective);
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
			if (stagingCreated) {
				try (var statement = connection.createStatement()) {
					statement.execute("DROP TABLE " + quote(stagingName));
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
				} catch (SQLException restoreFailure) {
					if (failure != null) {
						failure.addSuppressed(restoreFailure);
					} else if (cleanupFailure != null) {
						cleanupFailure.addSuppressed(restoreFailure);
					} else {
						throw restoreFailure;
					}
				}
			}
			if (failure == null && cleanupFailure != null) {
				throw cleanupFailure;
			}
		}
	}

	private long apply(final Connection connection, final String targetName,
			final String stagingName, final List<Column> keys,
			final List<Column> stagingColumns,
			final List<Column> updateColumns,
			final BulkUpsertOption options) throws SQLException {
		final String sql;
		if (!options.isInsertWhenNotMatched()) {
			sql = createUpdateSql(targetName, stagingName, keys, updateColumns);
		} else {
			sql = createInsertSql(targetName, stagingName, keys, stagingColumns,
					updateColumns, options);
		}
		try (var statement = connection.createStatement()) {
			return statement.executeUpdate(sql);
		}
	}

	private String createStagingSql(final String targetName,
			final String stagingName, final List<Column> columns) {
		return "CREATE TEMPORARY TABLE " + quote(stagingName)
				+ " AS SELECT " + columnList(columns, null) + " FROM "
				+ targetName + " WITH NO DATA";
	}

	private String createUpdateSql(final String targetName,
			final String stagingName, final List<Column> keys,
			final List<Column> updateColumns) {
		final StringBuilder sql = new StringBuilder("UPDATE ")
				.append(targetName).append(" AS target SET ");
		appendAssignments(sql, updateColumns);
		sql.append(" FROM ").append(quote(stagingName)).append(" AS source WHERE ");
		appendMatch(sql, keys);
		return sql.toString();
	}

	private String createInsertSql(final String targetName,
			final String stagingName, final List<Column> keys,
			final List<Column> stagingColumns,
			final List<Column> updateColumns,
			final BulkUpsertOption options) {
		final boolean identityInsert = stagingColumns.stream()
				.anyMatch(Column::isIdentity);
		final StringBuilder sql = new StringBuilder("INSERT INTO ")
				.append(targetName).append(" (")
				.append(columnList(stagingColumns, null)).append(')');
		if (identityInsert) {
			sql.append(" OVERRIDING SYSTEM VALUE");
		}
		sql.append(" SELECT ").append(columnList(stagingColumns, "source"))
				.append(" FROM ").append(quote(stagingName)).append(" AS source")
				.append(" ON CONFLICT (").append(columnList(keys, null)).append(')');
		if (options.isUpdateWhenMatched() && !updateColumns.isEmpty()) {
			sql.append(" DO UPDATE SET ");
			for (int i = 0; i < updateColumns.size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				final String name = quote(updateColumns.get(i).getName());
				sql.append(name).append(" = EXCLUDED.").append(name);
			}
		} else {
			sql.append(" DO NOTHING");
		}
		return sql.toString();
	}

	private void appendAssignments(final StringBuilder sql,
			final List<Column> columns) {
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			final String name = quote(columns.get(i).getName());
			sql.append(name).append(" = source.").append(name);
		}
	}

	private void appendMatch(final StringBuilder sql, final List<Column> keys) {
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			final String name = quote(keys.get(i).getName());
			sql.append("target.").append(name).append(" = source.").append(name);
		}
	}

	private BulkOption stagingBulkOption(final BulkOption source) {
		final BulkOption option = source == null ? BulkOption.defaults() : source;
		return BulkOption.builder().keepIdentity(true).keepNulls(true)
				.checkConstraints(option.isCheckConstraints()).build();
	}

	private String stagingName(final BulkUpsertOption options) {
		final String name = CommonUtils.isEmpty(options.getStagingTableName())
				? "sqlapp_upsert_" + UUID.randomUUID().toString().replace("-", "")
				: options.getStagingTableName();
		if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException(
					"Invalid PostgreSQL stagingTableName: " + name);
		}
		return name;
	}

	private Set<String> names(final List<Column> columns) {
		final Set<String> names = new HashSet<>();
		columns.forEach(column -> names.add(column.getName()));
		return names;
	}

	private String columnList(final List<Column> columns, final String alias) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			if (alias != null) {
				builder.append(alias).append('.');
			}
			builder.append(quote(columns.get(i).getName()));
		}
		return builder.toString();
	}

	private String quote(final String name) {
		return dialect.quote(name);
	}
}
