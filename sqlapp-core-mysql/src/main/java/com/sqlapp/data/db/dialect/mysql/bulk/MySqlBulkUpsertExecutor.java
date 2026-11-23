/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

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
import com.sqlapp.jdbc.bulk.BulkUpsertExecutionScope;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.util.CommonUtils;

/** MySQL/MariaDB bulk upsert using LOAD DATA and a temporary table. */
public class MySqlBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public MySqlBulkUpsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkUpsertOption options)
			throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		final BulkUpsertOption option = options == null ? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, option);
		final List<Column> keys = plan.getKeyColumns();
		final List<Column> staged = plan.getStagingColumns();
		final List<Column> updates = plan.getUpdateColumns();
		final String stage = stageName(option), stageSql = quote(stage);
		final String target = targetName(table);
		String marker = "sqlapp_bulk_matched";
		for (int suffix = 1; containsName(staged, marker); suffix++)
			marker = "sqlapp_bulk_matched_" + suffix;
		final String matched = quote(marker);
		try (var scope = BulkUpsertExecutionScope.begin(connection, option.isUseTransaction())) {
			try {
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE TEMPORARY TABLE " + stageSql
							+ (option.isInsertWhenNotMatched() ? " (" + matched + " TINYINT NOT NULL DEFAULT 0)" : "")
							+ " AS SELECT " + list(staged, null) + " FROM " + target + " WHERE 1 = 0");
				}
				scope.addCleanupSql("DROP TEMPORARY TABLE " + stageSql);
				BulkInsertResolver.resolve(dialect).execute(connection, plan.createStagingTable(stage),
						bulkOption(option.getBulkOption()));
				long affected = 0;
				try (var statement = connection.createStatement()) {
					final List<String> statements = sql(target, stageSql, keys, staged, updates, option, matched);
					for (int i = 0; i < statements.size(); i++) {
						final int count = statement.executeUpdate(statements.get(i));
						// The first statement only classifies rows in the staging table.
						if (!option.isInsertWhenNotMatched() || i != 0)
							affected += count;
					}
				}
				scope.commit();
				return affected;
			} catch (SQLException | RuntimeException e) {
				scope.rollback(e);
				throw e;
			}
		}
	}

	private boolean containsName(final List<Column> columns, final String name) {
		return columns.stream().anyMatch(column -> column.getName().equalsIgnoreCase(name));
	}

	private String targetName(final Table table) {
		final String catalog = table.getCatalogName();
		final String schema = table.getSchemaName();
		if (!CommonUtils.isEmpty(schema)) {
			// INFORMATION_SCHEMA uses catalog "def"; JDBC may use the database as catalog.
			if (!CommonUtils.isEmpty(catalog) && !"def".equals(catalog) && !catalog.equals(schema)) {
				throw new IllegalArgumentException(
						"Conflicting MySQL database identifiers: catalog=" + catalog + ", schema=" + schema);
			}
			return dialect.getObjectFullName(schema, table.getName());
		}
		return dialect.getObjectFullName(catalog, table.getName());
	}

	private List<String> sql(final String target, final String stage, final List<Column> keys,
			final List<Column> staged, final List<Column> updates, final BulkUpsertOption option,
			final String matched) {
		final List<String> statements = new java.util.ArrayList<>();
		if (option.isInsertWhenNotMatched()) {
			final StringBuilder classify = new StringBuilder("UPDATE ").append(stage).append(" AS source LEFT JOIN ")
					.append(target).append(" AS target ON ");
			match(classify, keys);
			classify.append(" SET source.").append(matched).append(" = CASE WHEN target.")
					.append(quote(keys.get(0).getName())).append(" IS NULL THEN 0 ELSE 1 END");
			statements.add(classify.toString());
		}
		if (option.isUpdateWhenMatched() && !updates.isEmpty()) {
			final StringBuilder s = new StringBuilder("UPDATE ").append(target).append(" AS target JOIN ").append(stage)
					.append(" AS source ON ");
			match(s, keys);
			s.append(" SET ");
			assignments(s, updates, "source");
			if (option.isInsertWhenNotMatched())
				s.append(" WHERE source.").append(matched).append(" = 1");
			statements.add(s.toString());
		}
		if (option.isInsertWhenNotMatched()) {
			final StringBuilder s = new StringBuilder("INSERT INTO ").append(target).append(" (")
					.append(list(staged, null)).append(") SELECT ").append(projection(staged)).append(" FROM ")
					.append(stage).append(" AS source");
			// Freeze match classification before target DML, so concurrent conflicts cannot
			// silently change an insert into an update or skip a requested action.
			s.append(" WHERE source.").append(matched).append(" = 0");
			statements.add(s.toString());
		}
		return statements;
	}

	private void match(final StringBuilder s, final List<Column> keys) {
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0)
				s.append(" AND ");
			final String n = quote(keys.get(i).getName());
			s.append("target.").append(n).append(" = source.").append(n);
		}
	}

	private void assignments(final StringBuilder s, final List<Column> cols, final String alias) {
		for (int i = 0; i < cols.size(); i++) {
			if (i > 0)
				s.append(", ");
			final String n = quote(cols.get(i).getName());
			s.append("target.").append(n).append(" = ").append(sourceValue(cols.get(i), alias));
		}
	}

	private String projection(final List<Column> columns) {
		return columns.stream().map(column -> sourceValue(column, "source"))
				.collect(java.util.stream.Collectors.joining(", "));
	}

	private String sourceValue(final Column column, final String alias) {
		final String reference = alias + "." + quote(column.getName());
		if (dialect instanceof com.sqlapp.data.db.dialect.mysql.MySql8019
				&& column.getDataType() == com.sqlapp.data.db.datatype.DataType.TIMESTAMPVERSION) {
			// A plain assignment can reconstruct TIMESTAMP from ambiguous local fields at a
			// DST overlap.
			return "CONCAT(TIMESTAMPADD(MICROSECOND, UNIX_TIMESTAMP(" + reference
					+ ") * 1000000, CAST('1970-01-01 00:00:00' AS DATETIME(6))), '+00:00')";
		}
		return reference;
	}

	private BulkOption bulkOption(final BulkOption source) {
		return source == null ? BulkOption.defaults() : source;
	}

	private String stageName(final BulkUpsertOption option) {
		final String n = CommonUtils.isEmpty(option.getStagingTableName())
				? "SQLAPP_UP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
				: option.getStagingTableName();
		if (!n.matches("[A-Za-z][A-Za-z0-9_$]{0,63}"))
			throw new IllegalArgumentException("Invalid MySQL stagingTableName: " + n);
		return n;
	}

	private Set<String> names(final List<Column> columns) {
		final Set<String> r = new HashSet<>();
		columns.forEach(c -> r.add(c.getName()));
		return r;
	}

	private String list(final List<Column> columns, final String alias) {
		final StringBuilder r = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0)
				r.append(", ");
			if (alias != null)
				r.append(alias).append('.');
			r.append(quote(columns.get(i).getName()));
		}
		return r.toString();
	}

	private String quote(final String name) {
		return dialect.quote(name);
	}

}
