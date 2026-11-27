/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/**
 * Bulk INSERT for DuckDB.
 * <p>
 * Rows are written with the DuckDB Appender when the runtime driver provides
 * the 1.4+ Appender API, every column of the target table is written from the
 * row, every column type is supported and no {@code bulkCopyTimeout} is set.
 * The Appender writes into DuckDB's column vectors directly and is much faster
 * than one INSERT per row. Values are converted with the same column converters
 * as the JDBC batch, so both transports store the same values.
 * </p>
 * <p>
 * Otherwise this is a JDBC batch INSERT. Values are bound with the column type
 * handlers registered by the DuckDB dialect (see
 * {@link com.sqlapp.data.db.dialect.duckdb.util.DuckDbJdbcTypeHandlers}); the
 * DuckDB JDBC driver rejects or corrupts several types when they are bound by
 * Java class. {@code insertMethod = JDBC_BATCH} always selects the batch.
 * </p>
 * <p>
 * Both transports take part in the caller's transaction. With auto-commit on,
 * rows the Appender has already flushed in earlier chunks remain after a later
 * failure, as completed batches do.
 * </p>
 */
public class DuckDbBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	private final Dialect dialect;

	public DuckDbBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
		this.dialect = dialect;
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		final BulkOption effective = options == null ? BulkOption.defaults() : options;
		if (isAppenderEnabled() && effective.getBulkCopyTimeout() == null && appenderAvailable()) {
			final long count = DuckDbAppenderBulkInsert.tryExecute(connection, dialect, table,
					writableColumns(table, effective));
			if (count >= 0) {
				return count;
			}
		}
		return super.execute(connection, table, options);
	}

	/**
	 * Whether the Appender may be used. The Appender writes the rows as an INSERT
	 * of every column, so a subclass that changes the statement with
	 * {@link #createInsertSql} or the binding with {@link #bind} must return
	 * {@code false}.
	 *
	 * @return {@code true} to use the Appender when it applies
	 */
	protected boolean isAppenderEnabled() {
		return true;
	}

	private static boolean appenderAvailable() {
		try {
			return DuckDbAppenderBulkInsert.isAvailable();
		} catch (LinkageError e) {
			// The DuckDB driver is not visible to this class loader.
			return false;
		}
	}
}
