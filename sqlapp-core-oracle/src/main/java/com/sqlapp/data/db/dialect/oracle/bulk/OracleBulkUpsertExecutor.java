/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

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

/** Oracle bulk upsert using JDBC batching, a global temp table and MERGE. */
public class OracleBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public OracleBulkUpsertExecutor(final Dialect dialect) {
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
		final List<Column> updates = plan.getUpdateColumns();
		final String stagingName = stagingName(effective);
		final String targetName = dialect.getObjectFullName(table.getCatalogName(),
				table.getSchemaName(), table.getName());
		boolean created = false;
		Throwable failure = null;
		SQLException cleanupFailure = null;
		try {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE GLOBAL TEMPORARY TABLE "
						+ quote(stagingName) + " ON COMMIT PRESERVE ROWS AS SELECT "
						+ columnList(stagingColumns, null) + " FROM " + targetName
						+ " WHERE 1 = 0");
				created = true;
			}
			BulkInsertResolver.resolve(dialect).execute(connection,
					plan.createStagingTable(stagingName),
					stagingBulkOption(effective.getBulkOption()));
			final long affected;
			try (var statement = connection.createStatement()) {
				affected = statement.executeUpdate(createMergeSql(targetName,
						stagingName, keys, stagingColumns, updates, effective));
			}
			return affected;
		} catch (SQLException | RuntimeException e) {
			failure = e;
			throw e;
		} finally {
			if (created) {
				try (var statement = connection.createStatement()) {
					statement.execute("TRUNCATE TABLE " + quote(stagingName));
					statement.execute("DROP TABLE " + quote(stagingName));
				} catch (SQLException e) {
					if (failure != null) {
						failure.addSuppressed(e);
					} else {
						cleanupFailure = e;
					}
				}
			}
			if (failure == null && cleanupFailure != null) {
				throw cleanupFailure;
			}
		}
	}

	private String createMergeSql(final String targetName,
			final String stagingName, final List<Column> keys,
			final List<Column> stagingColumns, final List<Column> updates,
			final BulkUpsertOption options) {
		final StringBuilder sql = new StringBuilder("MERGE INTO ")
				.append(targetName).append(" target USING ")
				.append(quote(stagingName)).append(" source ON (");
		appendMatch(sql, keys);
		sql.append(')');
		if (options.isUpdateWhenMatched() && !updates.isEmpty()) {
			sql.append(" WHEN MATCHED THEN UPDATE SET ");
			for (int i = 0; i < updates.size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				final String name = quote(updates.get(i).getName());
				sql.append("target.").append(name).append(" = source.")
						.append(name);
			}
		}
		if (options.isInsertWhenNotMatched()) {
			sql.append(" WHEN NOT MATCHED THEN INSERT (")
					.append(columnList(stagingColumns, null)).append(") VALUES (")
					.append(columnList(stagingColumns, "source")).append(')');
		}
		return sql.toString();
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

	private List<Column> resolveKeys(final Table table,
			final BulkUpsertOption options) {
		final List<String> names = new ArrayList<>(options.getKeyColumns());
		if (names.isEmpty()) {
			if (table.getPrimaryKeyConstraint() == null
					|| table.getPrimaryKeyConstraint().getColumns().isEmpty()) {
				throw new IllegalArgumentException(
						"Bulk upsert requires keyColumns or a primary key: "
								+ table.getName());
			}
			table.getPrimaryKeyConstraint().getColumns()
					.forEach(column -> names.add(column.getName()));
		}
		final List<Column> columns = columns(table, names, "key");
		if (columns.stream().anyMatch(Column::isIdentity)
				&& !options.getBulkOption().isKeepIdentity()) {
			throw new IllegalArgumentException(
					"An identity key requires bulkOption.keepIdentity=true");
		}
		return columns;
	}

	private List<Column> resolveStagingColumns(final Table table,
			final BulkUpsertOption options, final List<Column> keys) {
		final Set<String> keyNames = names(keys);
		final List<Column> columns = new ArrayList<>();
		for (final Column column : table.getColumns()) {
			if (!column.isHidden() && CommonUtils.isEmpty(column.getFormula())
					&& (!column.isIdentity()
							|| options.getBulkOption().isKeepIdentity()
							|| keyNames.contains(column.getName()))) {
				columns.add(column);
			}
		}
		if (!names(columns).containsAll(keyNames)) {
			throw new IllegalArgumentException(
					"Every key column must be writable to the staging table");
		}
		return columns;
	}

	private List<Column> resolveUpdateColumns(final Table table,
			final BulkUpsertOption options, final List<Column> keys,
			final List<Column> stagingColumns) {
		final Set<String> keyNames = names(keys);
		final Set<String> stagingNames = names(stagingColumns);
		if (!options.getUpdateColumns().isEmpty()) {
			final List<Column> columns = columns(table, options.getUpdateColumns(),
					"update");
			for (final Column column : columns) {
				if (keyNames.contains(column.getName()) || column.isIdentity()
						|| !stagingNames.contains(column.getName())) {
					throw new IllegalArgumentException(
							"Invalid bulk upsert update column: " + column.getName());
				}
			}
			return columns;
		}
		final List<Column> columns = new ArrayList<>();
		for (final Column column : stagingColumns) {
			if (!keyNames.contains(column.getName()) && !column.isIdentity()) {
				columns.add(column);
			}
		}
		return columns;
	}

	private List<Column> columns(final Table table, final List<String> names,
			final String role) {
		final List<Column> columns = new ArrayList<>();
		final Set<String> unique = new HashSet<>();
		for (final String name : names) {
			final Column column = table.getColumns().get(name);
			if (column == null || !unique.add(column.getName())) {
				throw new IllegalArgumentException("Invalid bulk upsert " + role
						+ " column: " + name);
			}
			columns.add(column);
		}
		return columns;
	}

	private Table stagingTable(final Table source, final String stagingName,
			final List<Column> columns) {
		final Table staging = new Table(stagingName) {
			private static final long serialVersionUID = 1L;

			@Override
			public RowCollection getRows() {
				return source.getRows();
			}
		};
		final Set<String> included = names(columns);
		for (final Column column : source.getColumns()) {
			final Column stagingColumn = column.clone().setIdentity(false);
			if (!included.contains(column.getName())) {
				stagingColumn.setHidden(true);
			}
			staging.getColumns().add(stagingColumn);
		}
		return staging;
	}

	private BulkOption stagingBulkOption(final BulkOption source) {
		final BulkOption option = source == null ? BulkOption.defaults() : source;
		return BulkOption.builder().batchSize(option.getBatchSize())
				.bulkCopyTimeout(option.getBulkCopyTimeout()).keepIdentity(true)
				.keepNulls(true).build();
	}

	private String stagingName(final BulkUpsertOption options) {
		final String name = CommonUtils.isEmpty(options.getStagingTableName())
				? "SQLAPP_UP_" + UUID.randomUUID().toString().replace("-", "")
						.substring(0, 16)
				: options.getStagingTableName();
		if (!name.matches("[A-Za-z][A-Za-z0-9_$#]{0,29}")) {
			throw new IllegalArgumentException("Invalid Oracle stagingTableName: "
					+ name);
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
