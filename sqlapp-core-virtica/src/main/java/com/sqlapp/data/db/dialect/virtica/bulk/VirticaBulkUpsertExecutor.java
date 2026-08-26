/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;

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
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.util.CommonUtils;

/** Vertica bulk upsert using COPY, a local temporary table and MERGE. */
public class VirticaBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public VirticaBulkUpsertExecutor(final Dialect d) {
		dialect = java.util.Objects.requireNonNull(d, "dialect");
	}

	@Override
	public long execute(final Connection c, final Table table, final BulkUpsertOption options) throws SQLException {
		java.util.Objects.requireNonNull(c, "connection");
		java.util.Objects.requireNonNull(table, "table");
		final BulkUpsertOption o = options == null ? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, o);
		final List<Column> keys = plan.getKeyColumns(), staged = plan.getStagingColumns(),
				updates = plan.getUpdateColumns();
		final String stage = stageName(o), stageSql = quote(stage),
				target = dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName());
		final boolean manage = o.isUseTransaction() && c.getAutoCommit();
		boolean created = false;
		Throwable failure = null;
		SQLException cleanup = null;
		try {
			if (manage)
				c.setAutoCommit(false);
			try (var s = c.createStatement()) {
				s.execute("CREATE LOCAL TEMPORARY TABLE " + stageSql + " ON COMMIT PRESERVE ROWS AS SELECT "
						+ list(staged, null) + " FROM " + target + " WHERE 1 = 0");
				created = true;
			}
			BulkInsertResolver.resolve(dialect).execute(c, plan.createStagingTable(stage), o.getBulkOption());
			final long affected = apply(c, target, stageSql, keys, staged, updates, o);
			if (manage)
				c.commit();
			return affected;
		} catch (SQLException | RuntimeException e) {
			failure = e;
			if (manage)
				try {
					c.rollback();
				} catch (SQLException x) {
					e.addSuppressed(x);
				}
			throw e;
		} finally {
			if (created)
				try (var s = c.createStatement()) {
					s.execute("DROP TABLE " + stageSql);
				} catch (SQLException e) {
					if (failure != null)
						failure.addSuppressed(e);
					else
						cleanup = e;
				}
			if (manage)
				try {
					c.setAutoCommit(true);
				} catch (SQLException e) {
					if (failure != null)
						failure.addSuppressed(e);
					else if (cleanup != null)
						cleanup.addSuppressed(e);
					else
						throw e;
				}
			if (failure == null && cleanup != null)
				throw cleanup;
		}
	}

	private long apply(final Connection c, final String target, final String stage, final List<Column> keys,
			final List<Column> staged, final List<Column> updates, final BulkUpsertOption o) throws SQLException {
		long affected = 0;
		try (var s = c.createStatement()) {
			if (o.isUpdateWhenMatched() && !updates.isEmpty()) {
				final StringBuilder sql = new StringBuilder("UPDATE ").append(target).append(" AS target SET ");
				for (int i = 0; i < updates.size(); i++) {
					if (i > 0)
						sql.append(", ");
					final String n = quote(updates.get(i).getName());
					sql.append(n).append(" = source.").append(n);
				}
				sql.append(" FROM ").append(stage).append(" AS source WHERE ");
				match(sql, keys);
				affected += s.executeUpdate(sql.toString());
			}
			if (o.isInsertWhenNotMatched()) {
				final StringBuilder sql = new StringBuilder("INSERT INTO ").append(target).append(" (")
						.append(list(staged, null)).append(") SELECT ").append(list(staged, "source")).append(" FROM ")
						.append(stage).append(" AS source WHERE NOT EXISTS (SELECT 1 FROM ").append(target)
						.append(" AS target WHERE ");
				match(sql, keys);
				sql.append(')');
				affected += s.executeUpdate(sql.toString());
			}
		}
		return affected;
	}

	private void match(final StringBuilder sql, final List<Column> keys) {
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0)
				sql.append(" AND ");
			final String n = quote(keys.get(i).getName());
			sql.append("target.").append(n).append(" = source.").append(n);
		}
	}

	private String stageName(final BulkUpsertOption o) {
		final String n = CommonUtils.isEmpty(o.getStagingTableName())
				? "SQLAPP_UP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
				: o.getStagingTableName();
		if (!n.matches("[A-Za-z][A-Za-z0-9_$]{0,127}"))
			throw new IllegalArgumentException("Invalid Vertica stagingTableName: " + n);
		return n;
	}

	private Set<String> names(final List<Column> c) {
		final Set<String> r = new HashSet<>();
		c.forEach(x -> r.add(x.getName()));
		return r;
	}

	private String list(final List<Column> c, final String a) {
		final StringBuilder r = new StringBuilder();
		for (int i = 0; i < c.size(); i++) {
			if (i > 0)
				r.append(", ");
			if (a != null)
				r.append(a).append('.');
			r.append(quote(c.get(i).getName()));
		}
		return r.toString();
	}

	private String quote(final String n) {
		return dialect.quote(n);
	}
}
