/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

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
import com.sqlapp.jdbc.bulk.BulkUpsertTransaction;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;
import com.sqlapp.util.CommonUtils;

/** Streaming SQLite 3.24+ upsert using Xerial JDBC batches. */
public class SqliteBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;

	public SqliteBulkUpsertExecutor(final Dialect d) {
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
		if (o.isInsertWhenNotMatched()) {
			bindings.addAll(writable);
			sql = insertSql(table, writable, keys, updates, o.isUpdateWhenMatched());
		} else {
			bindings.addAll(updates);
			bindings.addAll(keys);
			sql = updateSql(table, updates, keys);
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
		try (var transaction = BulkUpsertTransaction.begin(connection, o.isUseTransaction())) {
			try {
			final long affected = batch.execute(connection, plan.createStagingTable(table.getName()), o.getBulkOption());
			transaction.commit();
			return affected;
			} catch (SQLException | RuntimeException e) {
				transaction.rollback(e);
				throw e;
			}
		}
	}

	private String insertSql(final Table t, final List<Column> writable, final List<Column> keys,
			final List<Column> updates, final boolean update) {
		final StringBuilder s = new StringBuilder("INSERT INTO ").append(name(t)).append(" (").append(list(writable))
				.append(") VALUES (").append(params(writable.size())).append(") ON CONFLICT (").append(list(keys))
				.append(") DO ");
		if (!update || updates.isEmpty())
			return s.append("NOTHING").toString();
		s.append("UPDATE SET ");
		for (int i = 0; i < updates.size(); i++) {
			if (i > 0)
				s.append(", ");
			final String n = quote(updates.get(i).getName());
			s.append(n).append(" = excluded.").append(n);
		}
		return s.toString();
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

	private void where(final StringBuilder s, final List<Column> keys) {
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0)
				s.append(" AND ");
			s.append(quote(keys.get(i).getName())).append(" = ?");
		}
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
