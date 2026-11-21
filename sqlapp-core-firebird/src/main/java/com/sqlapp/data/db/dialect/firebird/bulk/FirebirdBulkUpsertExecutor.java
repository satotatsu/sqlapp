/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.jdbc.bulk.BulkUpsertTransaction;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/**
 * Streaming Firebird upsert backed by Jaybird JDBC batches.
 * <p>
 * Insert paths use {@code MERGE INTO target USING RDB$DATABASE}: the key
 * parameters sit in the {@code ON} comparison, update values in
 * {@code UPDATE SET} and insert values in {@code INSERT VALUES}, so every
 * parameter takes its type from the target column without a {@code CAST}. Only
 * {@code updateColumns} are updated; other columns of a matched row are kept.
 * MERGE requires Firebird 2.1. The update-only path is a plain {@code UPDATE}
 * and works on every supported version.
 * </p>
 */
public class FirebirdBulkUpsertExecutor implements BulkUpsertExecutor {
	private static final String TARGET = "TGT";

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
				updates = o.isUpdateWhenMatched() ? plan.getUpdateColumns() : List.of();
		final List<Column> bindings = new ArrayList<>();
		final String sql;
		if (!o.isInsertWhenNotMatched()) {
			bindings.addAll(updates);
			bindings.addAll(keys);
			sql = updateSql(table, updates, keys);
		} else {
			requireMerge(connection);
			bindings.addAll(keys);
			bindings.addAll(updates);
			bindings.addAll(writable);
			sql = mergeSql(table, writable, keys, updates);
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
				final long affected = batch.execute(connection, plan.createStagingTable(table.getName()),
						o.getBulkOption());
				transaction.commit();
				return affected;
			} catch (SQLException | RuntimeException e) {
				transaction.rollback(e);
				throw e;
			}
		}
	}

	/**
	 * MERGE exists from 2.1. A Firebird20 dialect may still be used against a 2.1
	 * server, so the server version decides there; 1.x never has MERGE.
	 */
	private void requireMerge(final Connection connection) throws SQLException {
		if (dialect.compareTo(DialectHolder.defaultDialect21) >= 0) {
			return;
		}
		if (dialect.compareTo(DialectHolder.defaultDialect20) >= 0) {
			final DatabaseMetaData metaData = connection.getMetaData();
			final int major = metaData.getDatabaseMajorVersion();
			if (major > 2 || major == 2 && metaData.getDatabaseMinorVersion() >= 1) {
				return;
			}
		}
		throw new SQLFeatureNotSupportedException(
				"Firebird bulk upsert with inserts requires MERGE, available from Firebird 2.1; "
						+ "use updateWhenMatched only, or upgrade the server");
	}

	private String mergeSql(final Table t, final List<Column> writable, final List<Column> keys,
			final List<Column> updates) {
		final StringBuilder s = new StringBuilder("MERGE INTO ").append(name(t)).append(' ').append(TARGET)
				.append(" USING RDB$DATABASE ON (");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0)
				s.append(" AND ");
			s.append(TARGET).append('.').append(quote(keys.get(i).getName())).append(" = ?");
		}
		s.append(')');
		if (!updates.isEmpty()) {
			s.append(" WHEN MATCHED THEN UPDATE SET ");
			assignments(s, updates);
		}
		s.append(" WHEN NOT MATCHED THEN INSERT (").append(list(writable)).append(") VALUES (")
				.append(params(writable.size())).append(')');
		return s.toString();
	}

	private String updateSql(final Table t, final List<Column> updates, final List<Column> keys) {
		final StringBuilder s = new StringBuilder("UPDATE ").append(name(t)).append(" SET ");
		assignments(s, updates);
		s.append(" WHERE ");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0)
				s.append(" AND ");
			s.append(quote(keys.get(i).getName())).append(" = ?");
		}
		return s.toString();
	}

	private void assignments(final StringBuilder s, final List<Column> columns) {
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0)
				s.append(", ");
			s.append(quote(columns.get(i).getName())).append(" = ?");
		}
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

	private static String params(final int count) {
		return String.join(", ", java.util.Collections.nCopies(count, "?"));
	}

	private String quote(final String n) {
		return dialect.quote(n);
	}
}
