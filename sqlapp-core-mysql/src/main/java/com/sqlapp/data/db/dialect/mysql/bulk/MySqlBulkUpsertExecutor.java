/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

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
		final String target = dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName());
		final boolean manage = option.isUseTransaction() && connection.getAutoCommit();
		boolean created = false;
		Throwable failure = null;
		SQLException cleanupFailure = null;
		try {
			if (manage)
				connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TEMPORARY TABLE " + stageSql + " AS SELECT " + list(staged, null) + " FROM "
						+ target + " WHERE 1 = 0");
				created = true;
			}
			BulkInsertResolver.resolve(dialect).execute(connection, plan.createStagingTable(stage),
					bulkOption(option.getBulkOption()));
			final long affected;
			try (var statement = connection.createStatement()) {
				affected = statement.executeUpdate(sql(target, stageSql, keys, staged, updates, option));
			}
			if (manage)
				connection.commit();
			return affected;
		} catch (SQLException | RuntimeException e) {
			failure = e;
			if (manage)
				try {
					connection.rollback();
				} catch (SQLException x) {
					e.addSuppressed(x);
				}
			throw e;
		} finally {
			if (created)
				try (var statement = connection.createStatement()) {
					statement.execute("DROP TEMPORARY TABLE " + stageSql);
				} catch (SQLException e) {
					if (failure != null)
						failure.addSuppressed(e);
					else
						cleanupFailure = e;
				}
			if (manage)
				try {
					connection.setAutoCommit(true);
				} catch (SQLException e) {
					if (failure != null)
						failure.addSuppressed(e);
					else if (cleanupFailure != null)
						cleanupFailure.addSuppressed(e);
					else
						throw e;
				}
			if (failure == null && cleanupFailure != null)
				throw cleanupFailure;
		}
	}

	private String sql(final String target, final String stage, final List<Column> keys, final List<Column> staged,
			final List<Column> updates, final BulkUpsertOption option) {
		if (!option.isInsertWhenNotMatched()) {
			final StringBuilder s = new StringBuilder("UPDATE ").append(target).append(" AS target JOIN ").append(stage)
					.append(" AS source ON ");
			match(s, keys);
			s.append(" SET ");
			assignments(s, updates, "source");
			return s.toString();
		}
		final StringBuilder s = new StringBuilder("INSERT INTO ").append(target).append(" (").append(list(staged, null))
				.append(") SELECT ").append(list(staged, "source")).append(" FROM ").append(stage)
				.append(" AS source WHERE 1 = 1");
		if (option.isUpdateWhenMatched() && !updates.isEmpty()) {
			s.append(" ON DUPLICATE KEY UPDATE ");
			for (int i = 0; i < updates.size(); i++) {
				if (i > 0)
					s.append(", ");
				final String n = quote(updates.get(i).getName());
				s.append(n).append(" = VALUES(").append(n).append(')');
			}
		} else
			s.append(" ON DUPLICATE KEY UPDATE ").append(quote(keys.get(0).getName())).append(" = ")
					.append(quote(keys.get(0).getName()));
		return s.toString();
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
			s.append("target.").append(n).append(" = ").append(alias).append('.').append(n);
		}
	}

	private List<Column> keys(final Table table, final BulkUpsertOption option) {
		final List<String> names = new ArrayList<>(option.getKeyColumns());
		if (names.isEmpty()) {
			if (table.getPrimaryKeyConstraint() == null || table.getPrimaryKeyConstraint().getColumns().isEmpty())
				throw new IllegalArgumentException(
						"Bulk upsert requires keyColumns or a primary key: " + table.getName());
			table.getPrimaryKeyConstraint().getColumns().forEach(c -> names.add(c.getName()));
		}
		final List<Column> result = columns(table, names, "key");
		if (result.stream().anyMatch(Column::isIdentity) && !option.getBulkOption().isKeepIdentity())
			throw new IllegalArgumentException("An identity key requires bulkOption.keepIdentity=true");
		return result;
	}

	private List<Column> staged(final Table table, final BulkUpsertOption option, final List<Column> keys) {
		final Set<String> keyNames = names(keys);
		final List<Column> result = new ArrayList<>();
		for (final Column c : table.getColumns())
			if (!c.isHidden() && CommonUtils.isEmpty(c.getFormula())
					&& (!c.isIdentity() || option.getBulkOption().isKeepIdentity() || keyNames.contains(c.getName())))
				result.add(c);
		if (!names(result).containsAll(keyNames))
			throw new IllegalArgumentException("Every key column must be writable to the staging table");
		return result;
	}

	private List<Column> updates(final Table table, final BulkUpsertOption option, final List<Column> keys,
			final List<Column> staged) {
		final Set<String> keyNames = names(keys), stagedNames = names(staged);
		if (!option.getUpdateColumns().isEmpty()) {
			final List<Column> result = columns(table, option.getUpdateColumns(), "update");
			for (final Column c : result)
				if (keyNames.contains(c.getName()) || c.isIdentity() || !stagedNames.contains(c.getName()))
					throw new IllegalArgumentException("Invalid bulk upsert update column: " + c.getName());
			return result;
		}
		final List<Column> result = new ArrayList<>();
		for (final Column c : staged)
			if (!keyNames.contains(c.getName()) && !c.isIdentity())
				result.add(c);
		return result;
	}

	private List<Column> columns(final Table table, final List<String> names, final String role) {
		final List<Column> result = new ArrayList<>();
		final Set<String> unique = new HashSet<>();
		for (final String name : names) {
			final Column c = table.getColumns().get(name);
			if (c == null || !unique.add(c.getName()))
				throw new IllegalArgumentException("Invalid bulk upsert " + role + " column: " + name);
			result.add(c);
		}
		return result;
	}

	private Table stagingTable(final Table source, final String name, final List<Column> staged) {
		final Table table = new Table(name) {
			private static final long serialVersionUID = 1L;

			@Override
			public RowCollection getRows() {
				return source.getRows();
			}
		};
		final Set<String> included = names(staged);
		for (final Column c : source.getColumns()) {
			final Column copy = c.clone().setIdentity(false);
			if (!included.contains(c.getName()))
				copy.setHidden(true);
			table.getColumns().add(copy);
		}
		return table;
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
