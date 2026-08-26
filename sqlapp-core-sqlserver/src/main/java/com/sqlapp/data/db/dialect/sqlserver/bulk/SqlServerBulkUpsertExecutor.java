/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.jdbc.bulk.BulkUpsertTransaction;
import com.sqlapp.util.CommonUtils;

/** SQL Server bulk upsert using SQLServerBulkCopy and a local temp table. */
public class SqlServerBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public SqlServerBulkUpsertExecutor(final Dialect dialect) {
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
		final BulkUpsertTransaction transaction = BulkUpsertTransaction.begin(connection,
				effective.isUseTransaction());
		boolean stagingCreated = false;
		Throwable failure = null;
		SQLException cleanupFailure = null;
		try {
			try (var statement = connection.createStatement()) {
				statement.execute(createStagingSql(targetName, stagingName,
						stagingColumns));
				stagingCreated = true;
			}
			final Table staging = plan.createStagingTable(stagingName);
			BulkInsertResolver.resolve(dialect).execute(connection, staging,
					stagingBulkOption(effective.getBulkOption()));
			final long affected = executeMerge(connection, table, targetName,
					stagingName, keys, stagingColumns, updateColumns, effective);
			transaction.commit();
			return affected;
		} catch (SQLException | RuntimeException e) {
			failure = e;
			transaction.rollback(e);
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
			try { transaction.close(); } catch (SQLException restoreFailure) {
				if (failure != null) failure.addSuppressed(restoreFailure);
				else if (cleanupFailure != null) cleanupFailure.addSuppressed(restoreFailure);
				else throw restoreFailure;
			}
			if (failure == null && cleanupFailure != null) {
				throw cleanupFailure;
			}
		}
	}

	private long executeMerge(final Connection connection, final Table table,
			final String targetName, final String stagingName,
			final List<Column> keys, final List<Column> stagingColumns,
			final List<Column> updateColumns,
			final BulkUpsertOption options) throws SQLException {
		final boolean identityInsert = options.isInsertWhenNotMatched()
				&& stagingColumns.stream().anyMatch(Column::isIdentity);
		SQLException failure = null;
		try (var statement = connection.createStatement()) {
			if (identityInsert) {
				statement.execute("SET IDENTITY_INSERT " + targetName + " ON");
			}
			return statement.executeUpdate(createMergeSql(targetName, stagingName,
					keys, stagingColumns, updateColumns, options));
		} catch (SQLException e) {
			failure = e;
			throw e;
		} finally {
			if (identityInsert) {
				try (var statement = connection.createStatement()) {
					statement.execute("SET IDENTITY_INSERT " + targetName + " OFF");
				} catch (SQLException offFailure) {
					if (failure != null) {
						failure.addSuppressed(offFailure);
					} else {
						throw offFailure;
					}
			}
		}
	}
	}

	private String createStagingSql(final String targetName,
			final String stagingName, final List<Column> columns) {
		final String columnList = columnList(columns, null);
		return "SELECT TOP (0) " + columnList + " INTO " + quote(stagingName)
				+ " FROM " + targetName + " UNION ALL SELECT TOP (0) "
				+ columnList + " FROM " + targetName;
	}

	private String createMergeSql(final String targetName,
			final String stagingName, final List<Column> keys,
			final List<Column> stagingColumns,
			final List<Column> updateColumns,
			final BulkUpsertOption options) {
		final StringBuilder sql = new StringBuilder("MERGE INTO ")
				.append(targetName).append(" WITH (HOLDLOCK) AS target USING ")
				.append(quote(stagingName)).append(" AS source ON ");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			sql.append("target.").append(quote(keys.get(i).getName()))
					.append(" = source.").append(quote(keys.get(i).getName()));
		}
		if (options.isUpdateWhenMatched() && !updateColumns.isEmpty()) {
			sql.append(" WHEN MATCHED THEN UPDATE SET ");
			for (int i = 0; i < updateColumns.size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				final String name = quote(updateColumns.get(i).getName());
				sql.append("target.").append(name).append(" = source.")
						.append(name);
			}
		}
		if (options.isInsertWhenNotMatched()) {
			sql.append(" WHEN NOT MATCHED BY TARGET THEN INSERT (")
					.append(columnList(stagingColumns, null)).append(") VALUES (")
					.append(columnList(stagingColumns, "source")).append(')');
		}
		return sql.append(';').toString();
	}

	private BulkOption stagingBulkOption(final BulkOption source) {
		final BulkOption option = source == null ? BulkOption.defaults() : source;
		return BulkOption.builder().batchSize(option.getBatchSize())
				.bulkCopyTimeout(option.getBulkCopyTimeout()).keepIdentity(true)
				.keepNulls(true).tableLock(option.isTableLock()).build();
	}

	private String stagingName(final BulkUpsertOption options) {
		final String name = CommonUtils.isEmpty(options.getStagingTableName())
				? "#sqlapp_upsert_" + UUID.randomUUID().toString().replace("-", "")
				: options.getStagingTableName();
		if (!name.matches("#[A-Za-z0-9_]+")) {
			throw new IllegalArgumentException(
					"SQL Server stagingTableName must be a local temp table: " + name);
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
