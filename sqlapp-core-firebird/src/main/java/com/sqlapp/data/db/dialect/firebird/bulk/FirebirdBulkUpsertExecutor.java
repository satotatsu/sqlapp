/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;
import com.sqlapp.util.CommonUtils;

/** Streaming Firebird upsert backed by Jaybird JDBC batches. */
public class FirebirdBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public FirebirdBulkUpsertExecutor(final Dialect d) {
		dialect = java.util.Objects.requireNonNull(d, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkUpsertOption options)
			throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		final BulkUpsertOption o = options == null ? BulkUpsertOption.defaults() : options;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(table, o);
		final List<Column> keys = plan.getKeyColumns(), writable = plan.getStagingColumns(),
				updates = plan.getUpdateColumns();
		final List<Column> bindings = new ArrayList<>();
		final String sql;
		if (o.isUpdateWhenMatched() && o.isInsertWhenNotMatched()) {
			bindings.addAll(writable);
			sql = upsertSql(table, writable, keys);
		} else if (o.isUpdateWhenMatched()) {
			bindings.addAll(updates);
			bindings.addAll(keys);
			sql = updateSql(table, updates, keys);
		} else {
			bindings.addAll(writable);
			bindings.addAll(keys);
			sql = insertOnlySql(table, writable, keys);
		}
		final JdbcBatchBulkInsertExecutor batch = new JdbcBatchBulkInsertExecutor(dialect) {
			@Override
			protected List<Column> writableColumns(final Table ignored, final BulkOption option) {
				return bindings;
			}

			@Override
			protected String createInsertSql(final Table ignored, final List<Column> columns) {
				return sql;
			}
		};
		final boolean manage = o.isUseTransaction() && connection.getAutoCommit();
		try {
			if (manage)
				connection.setAutoCommit(false);
			final long affected = batch.execute(connection, plan.createStagingTable(table.getName()), o.getBulkOption());
			if (manage)
				connection.commit();
			return affected;
		} catch (SQLException | RuntimeException e) {
			if (manage)
				try {
					connection.rollback();
				} catch (SQLException x) {
					e.addSuppressed(x);
				}
			throw e;
		} finally {
			if (manage)
				connection.setAutoCommit(true);
		}
	}

	private String upsertSql(final Table t, final List<Column> writable, final List<Column> keys) {
		return "UPDATE OR INSERT INTO " + name(t) + " (" + list(writable) + ") VALUES (" + params(writable.size())
				+ ") MATCHING (" + list(keys) + ")";
	}

	private String updateSql(final Table t, final List<Column> updates, final List<Column> keys) {
		final StringBuilder s = new StringBuilder("UPDATE ").append(name(t)).append(" SET ");
		for (int i = 0; i < updates.size(); i++) {
			if (i > 0)
				s.append(", ");
			s.append(quote(updates.get(i).getName())).append(" = ?");
		}
		s.append(" WHERE ");
		where(s, keys);
		return s.toString();
	}

	private String insertOnlySql(final Table t, final List<Column> writable, final List<Column> keys) {
		final StringBuilder s = new StringBuilder("INSERT INTO ").append(name(t)).append(" (").append(list(writable))
				.append(") SELECT ").append(params(writable.size()))
				.append(" FROM RDB$DATABASE WHERE NOT EXISTS (SELECT 1 FROM ").append(name(t)).append(" WHERE ");
		where(s, keys);
		return s.append(')').toString();
	}

	private void where(final StringBuilder s, final List<Column> keys) {
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0)
				s.append(" AND ");
			s.append(quote(keys.get(i).getName())).append(" = ?");
		}
	}

	private List<Column> keys(final Table t, final BulkUpsertOption o) {
		final List<String> n = new ArrayList<>(o.getKeyColumns());
		if (n.isEmpty()) {
			if (t.getPrimaryKeyConstraint() == null || t.getPrimaryKeyConstraint().getColumns().isEmpty())
				throw new IllegalArgumentException("Bulk upsert requires keyColumns or a primary key: " + t.getName());
			t.getPrimaryKeyConstraint().getColumns().forEach(c -> n.add(c.getName()));
		}
		final List<Column> r = columns(t, n, "key");
		if (r.stream().anyMatch(Column::isIdentity) && !o.getBulkOption().isKeepIdentity())
			throw new IllegalArgumentException("An identity key requires bulkOption.keepIdentity=true");
		return r;
	}

	private List<Column> writable(final Table t, final BulkUpsertOption o) {
		final Set<String> kn = names(keys(t, o));
		final List<Column> r = new ArrayList<>();
		for (final Column c : t.getColumns())
			if (!c.isHidden() && CommonUtils.isEmpty(c.getFormula())
					&& (!c.isIdentity() || o.getBulkOption().isKeepIdentity() || kn.contains(c.getName())))
				r.add(c);
		if (!names(r).containsAll(kn))
			throw new IllegalArgumentException("Every key column must be writable");
		return r;
	}

	private List<Column> updates(final Table t, final BulkUpsertOption o, final List<Column> keys,
			final List<Column> writable) {
		final Set<String> kn = names(keys), wn = names(writable);
		if (!o.getUpdateColumns().isEmpty()) {
			final List<Column> r = columns(t, o.getUpdateColumns(), "update");
			for (final Column c : r)
				if (kn.contains(c.getName()) || c.isIdentity() || !wn.contains(c.getName()))
					throw new IllegalArgumentException("Invalid bulk upsert update column: " + c.getName());
			return r;
		}
		final List<Column> r = new ArrayList<>();
		for (final Column c : writable)
			if (!kn.contains(c.getName()) && !c.isIdentity())
				r.add(c);
		return r;
	}

	private List<Column> columns(final Table t, final List<String> names, final String role) {
		final List<Column> r = new ArrayList<>();
		final Set<String> u = new HashSet<>();
		for (final String n : names) {
			final Column c = t.getColumns().get(n);
			if (c == null || !u.add(c.getName()))
				throw new IllegalArgumentException("Invalid bulk upsert " + role + " column: " + n);
			r.add(c);
		}
		return r;
	}

	private Set<String> names(final List<Column> c) {
		final Set<String> r = new HashSet<>();
		c.forEach(x -> r.add(x.getName()));
		return r;
	}

	private String name(final Table t) {
		return dialect.getObjectFullName(t.getSchemaName(), t.getName());
	}

	private String list(final List<Column> c) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < c.size(); i++) {
			if (i > 0)
				s.append(", ");
			s.append(quote(c.get(i).getName()));
		}
		return s.toString();
	}

	private String params(final int count) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (i > 0)
				s.append(", ");
			s.append('?');
		}
		return s.toString();
	}

	private String quote(final String n) {
		return dialect.quote(n);
	}
}
