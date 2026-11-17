/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import com.mysql.cj.jdbc.JdbcStatement;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;

/** MySQL bulk insert backed by LOAD DATA LOCAL INFILE. */
public class MySqlBulkInsertExecutor implements BulkInsertExecutor {
	private final Dialect dialect;

	public MySqlBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	/** User variable that carries the abort flag of each record. */
	protected static final String ABORT_VARIABLE = "@sqlapp_abort";

	/**
	 * An expression that raises an error (not a warning, which LOAD DATA LOCAL downgrades) when the abort variable is
	 * not NULL, or null when the server has none. It must depend on the variable, so that it is evaluated only for the
	 * abort record. Without it, a value that cannot be encoded aborts the stream and the driver drops the connection.
	 * <p>
	 * MySQL 5.7+: an invalid JSON cast. (A subquery returning two rows is only a warning on MySQL.)
	 * </p>
	 *
	 * @param variable abort variable
	 * @return the expression, or null
	 */
	protected String abortExpression(final String variable) {
		if (dialect instanceof com.sqlapp.data.db.dialect.mysql.MySql570) {
			return "CAST(CONCAT('{', " + variable + ") AS JSON)";
		}
		return null;
	}

	/** Driver-specific stream binding; MariaDB overrides only this operation. */
	protected void bindInput(java.sql.Statement statement, java.io.InputStream input) throws SQLException {
		statement.unwrap(JdbcStatement.class).setLocalInfileInputStream(input);
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		final boolean own = options != null && options.isUseTransaction() && connection.getAutoCommit();
		if (options != null && options.isUseTransaction()) verifyEngine(connection, table);
		String state = connection.getAutoCommit() ? "AUTOCOMMIT: partial rows may already be committed" : "CALLER_TRANSACTION: caller must decide rollback";
		long affected = -1;
		final MySqlBulkDataInputStream input = new MySqlBulkDataInputStream(table, options);
		boolean committing = false;
		try {
			if (own) {
				try { connection.setAutoCommit(false); }
				catch (SQLException failure) {
					try { input.close(); } catch (IOException close) { failure.addSuppressed(close); }
					throw failure;
				}
			}
			try (input; var statement = connection.createStatement()) {
				final StringBuilder sql = new StringBuilder("LOAD DATA LOCAL INFILE " + "'sqlapp-stream' INTO TABLE ")
						.append(dialect.getObjectFullName(table.getSchemaName(), table.getName()))
						.append(" CHARACTER SET utf8mb4 FIELDS TERMINATED BY X'1F' "
								+ "ESCAPED BY X'5C' LINES TERMINATED BY X'1E' (");
				final String abort = abortExpression(ABORT_VARIABLE);
				input.setAbortField(abort != null);
				final java.util.List<String> assignments = new java.util.ArrayList<>();
				for (int i = 0; i < input.getColumns().size(); i++) {
					if (i > 0) {
						sql.append(", ");
					}
					final var column = input.getColumns().get(i);
					final String value;
					if (isVariableColumn(column)) {
						sql.append("@sqlapp_bulk_").append(i);
						value = column.getDataType() == DataType.BIT ? "CAST(CONV(@sqlapp_bulk_" + i + ",2,10) AS UNSIGNED)"
								: "UNHEX(@sqlapp_bulk_" + i + ")";
					} else {
						sql.append(dialect.quote(column.getName()));
						value = null;
					}
					if (i == 0 && abort != null) {
						// Guard: keeps the loaded value, and fails the statement on the abort record. The failing branch
						// is wrapped so that it is typed NULL and does not change the column value's type.
						final String loaded = value != null ? value : dialect.quote(column.getName());
						assignments.add(dialect.quote(column.getName()) + "=IF(" + ABORT_VARIABLE + " IS NULL, " + loaded
								+ ", IF(" + abort + " IS NULL, NULL, NULL))");
					} else if (value != null) {
						assignments.add(dialect.quote(column.getName()) + "=" + value);
					}
				}
				if (abort != null) {
					sql.append(", ").append(ABORT_VARIABLE);
				}
				sql.append(')');
				for (int i = 0; i < assignments.size(); i++) {
					sql.append(i == 0 ? " SET " : ", ").append(assignments.get(i));
				}
				bindInput(statement, input);
				Throwable primary = null;
				try {
					affected = statement.executeLargeUpdate(sql.toString());
					final var warning = statement.getWarnings();
					if (warning != null) throw new SQLException("LOAD DATA warning " + warning.getErrorCode() + ": " + warning.getMessage(), warning.getSQLState(), warning.getErrorCode(), warning);
					try (var diagnostics = connection.createStatement(); var result = diagnostics.executeQuery("SHOW COUNT(*) WARNINGS")) {
						if (!result.next() || result.getLong(1) != 0) throw new SQLException("LOAD DATA server reported warnings (details may be suppressed by max_error_count)");
					}
					if (!input.isComplete() || input.getRowCount() != affected) throw new SQLException("Incomplete or mismatched LOAD DATA result");
				} catch (SQLException | RuntimeException | Error e) {
					primary = e;
					throw e;
				} finally {
					try { bindInput(statement, null); }
					catch (SQLException | RuntimeException e) { if (primary != null) primary.addSuppressed(e); else throw e; }
				}
			}
			if (own) {
				committing = true;
				connection.commit();
				state = "COMMITTED: retry is unsafe";
				connection.setAutoCommit(true);
			}
			return affected;
		} catch (SQLException | IOException | RuntimeException | Error e) {
			if (own && !state.startsWith("COMMITTED")) {
				try {
					connection.rollback();
					state = committing ? "COMMIT_UNKNOWN: inspect database before retry" : "ROLLED_BACK";
					try { connection.setAutoCommit(true); } catch (SQLException reset) { e.addSuppressed(reset); }
				} catch (SQLException rollback) {
					state = "ROLLBACK_FAILED: connection left in transaction; inspect database";
					e.addSuppressed(rollback);
				}
			}
			if (e instanceof Error error) throw error;
			// A value that could not be encoded ended the stream with an abort record: report that value's failure,
			// with the server's abort error attached.
			final Throwable cause = input.getFailure() != null ? input.getFailure() : e;
			final SQLException failure = new SQLException("LOAD DATA failed: sent=" + input.getRowCount() + ", affected=" + affected
					+ ", complete=" + input.isComplete() + ", " + state + "; " + cause.getMessage(),
					e instanceof SQLException sql ? sql.getSQLState() : null,
					e instanceof SQLException sql ? sql.getErrorCode() : 0, cause);
			if (cause != e) failure.addSuppressed(e);
			for (Throwable suppressed : e.getSuppressed()) failure.addSuppressed(suppressed);
			throw failure;
		}
	}

	private void verifyEngine(Connection connection, Table table) throws SQLException {
		try (var statement = connection.prepareStatement("SELECT ENGINE FROM information_schema.tables WHERE TABLE_SCHEMA=COALESCE(?,DATABASE()) AND TABLE_NAME=?")) {
			statement.setString(1, table.getSchemaName());
			statement.setString(2, table.getName());
			try (var result = statement.executeQuery()) {
				if (!result.next() || !"InnoDB".equalsIgnoreCase(result.getString(1)))
					throw new SQLException("LOAD DATA useTransaction requires a verifiable InnoDB target (temporary tables are not verifiable)");
			}
		}
	}

	private void validateOptions(final BulkOption options) {
		if (options != null && (options.getBatchSize() != null || options.getBulkCopyTimeout() != null
				|| options.isAllowEncryptedValueModifications() || options.isCheckConstraints()
				|| options.isFireTriggers() || options.isKeepNulls() || options.isTableLock())) {
			throw new IllegalArgumentException("MySQL LOAD DATA does not support the requested bulk options");
		}
	}

	/**
	 * LOAD DATA でユーザー変数に読み込み、SET 句で変換する列か。バイナリ・BIT・GEOMETRY の列が対象。 GEOMETRY は通常の INSERT と同じ
	 * MySQL 内部形式を16進文字列で送り、UNHEX で戻す。
	 */
	private static boolean isVariableColumn(final com.sqlapp.data.schemas.Column column) {
		return column.getDataType() != null && (column.getDataType().isBinary() || column.getDataType() == DataType.BIT
				|| column.getDataType() == DataType.GEOMETRY);
	}
}
