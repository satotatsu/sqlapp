/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Serializable-transaction lease store backed by a target control table. */
public final class JdbcBulkMigrationJobLeaseStore
		implements BulkMigrationJobLeaseStore {
	public static final String DEFAULT_TABLE_NAME = "sqlapp_bulk_job_lease";
	private static final int SERIALIZATION_RETRY_LIMIT = 8;
	private static final long SERIALIZATION_RETRY_DELAY_MILLIS = 25L;

	private final Connection connection;
	private final String rawTableName;
	private final Dialect dialect;
	private final Map<SqlType, SqlNode> sqlNodes = new EnumMap<>(SqlType.class);

	public JdbcBulkMigrationJobLeaseStore(final Connection connection)
			throws SQLException {
		this(connection, DEFAULT_TABLE_NAME);
	}

	public JdbcBulkMigrationJobLeaseStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid lease table name: " + tableName);
		}
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.rawTableName = tableName;
		final Table table = JdbcBulkMigrationJobLeaseTable.table(rawTableName);
		final var registry = dialect.createSqlFactoryRegistry();
		for (SqlType type : new SqlType[] { SqlType.SELECT, SqlType.UPDATE,
				SqlType.INSERT, SqlType.DELETE }) {
			sqlNodes.put(type, registry.createSqlNodes(table, type).get(0));
		}
		ensureTable();
	}

	@Override
	public Optional<BulkMigrationJobLease> load(final String planFingerprint)
			throws SQLException {
		JdbcBulkMigrationJobLeaseTable.validateFingerprint(planFingerprint);
		return loadInternal(planFingerprint);
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now)
			throws SQLException {
		validateCandidate(lease, now);
		try {
			return transaction(() -> {
				final BulkMigrationJobLease current = loadInternal(
						lease.planFingerprint()).orElse(null);
				if (current != null && !current.isExpiredAt(now)) {
					return false;
				}
				write(lease, current == null ? SqlType.INSERT : SqlType.UPDATE);
				return true;
			}, true);
		} catch (SQLException e) {
			if (isInformixLockConflict(e)) {
				return false;
			}
			throw e;
		}
	}

	@Override
	public boolean renew(final BulkMigrationJobLease lease, final Instant now)
			throws SQLException {
		validateCandidate(lease, now);
		return transaction(() -> {
			final BulkMigrationJobLease current = loadInternal(lease.planFingerprint())
					.orElse(null);
			if (current == null || current.isExpiredAt(now)
					|| !current.ownerId().equals(lease.ownerId())) {
				return false;
			}
			write(lease, SqlType.UPDATE);
			return true;
		});
	}

	@Override
	public void release(final String planFingerprint, final String ownerId)
			throws SQLException {
		new BulkMigrationJobLease(planFingerprint, ownerId, Instant.MAX);
		transaction(() -> {
			final BulkMigrationJobLease current = loadInternal(planFingerprint)
					.orElse(null);
			if (current != null && current.ownerId().equals(ownerId)) {
				new JdbcHandler(sqlNodes.get(SqlType.DELETE)).execute(connection,
						JdbcBulkMigrationJobLeaseTable.parameters(planFingerprint));
			}
			return null;
		});
	}

	private Optional<BulkMigrationJobLease> loadInternal(final String fingerprint)
			throws SQLException {
		final BulkMigrationJobLease[] result = new BulkMigrationJobLease[1];
		new JdbcHandler(sqlNodes.get(SqlType.SELECT),
				rs -> result[0] = JdbcBulkMigrationJobLeaseTable.lease(rs,
						fingerprint)).execute(connection,
						JdbcBulkMigrationJobLeaseTable.parameters(fingerprint));
		return Optional.ofNullable(result[0]);
	}

	private void write(final BulkMigrationJobLease lease, final SqlType type)
			throws SQLException {
		new JdbcHandler(sqlNodes.get(type)).execute(connection,
				JdbcBulkMigrationJobLeaseTable.parameters(lease));
	}

	private <T> T transaction(final SqlCallable<T> callable) throws SQLException {
		return transaction(callable, false);
	}

	private <T> T transaction(final SqlCallable<T> callable,
			final boolean retryUniqueViolation) throws SQLException {
		for (int attempt = 0;; attempt++) {
			try {
				return transactionOnce(callable);
			} catch (SQLException e) {
				if (attempt >= SERIALIZATION_RETRY_LIMIT
						|| !(isSerializationFailure(e)
								|| retryUniqueViolation && isUniqueViolation(e))) {
					throw e;
				}
				serializationRetryDelay(attempt, e);
			}
		}
	}

	private static void serializationRetryDelay(final int attempt,
			final SQLException failure) throws SQLException {
		try {
			final long jitter = ThreadLocalRandom.current()
					.nextLong(SERIALIZATION_RETRY_DELAY_MILLIS);
			Thread.sleep(SERIALIZATION_RETRY_DELAY_MILLIS * (attempt + 1)
					+ jitter);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			failure.addSuppressed(e);
			throw failure;
		}
	}

	private <T> T transactionOnce(final SqlCallable<T> callable)
			throws SQLException {
		if (!connection.getAutoCommit()) {
			throw new IllegalStateException(
					"JDBC lease store requires a dedicated auto-commit connection");
		}
		final int isolation = connection.getTransactionIsolation();
		connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		try {
			connection.setAutoCommit(false);
		} catch (SQLException | RuntimeException e) {
			restoreConnection(isolation, e);
			throw e;
		}
		try {
			final T result = callable.call();
			connection.commit();
			restoreConnection(isolation, null);
			return result;
		} catch (SQLException | RuntimeException | Error e) {
			try {
				connection.rollback();
			} catch (SQLException rollbackFailure) {
				e.addSuppressed(rollbackFailure);
			}
			restoreConnection(isolation, e);
			throw e;
		}
	}

	private boolean isSerializationFailure(final SQLException failure) {
		for (SQLException current = failure; current != null;
				current = current.getNextException()) {
			if (current instanceof SQLTransactionRollbackException
					|| "40001".equals(current.getSQLState())
					|| current.getErrorCode() == 8177
					|| isSqliteBusy(current)
					|| isInformixLockConflict(current)
					|| isSapHanaSerializationFailure(current)
					|| isSpannerAbort(current)
					|| isSybaseDeadlock(current)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSqliteBusy(final SQLException failure) {
		return "SQLite".equalsIgnoreCase(dialect.getProductName())
				&& failure.getErrorCode() == 5;
	}

	private boolean isInformixLockConflict(final SQLException failure) {
		final boolean informix = containsIgnoreCase(dialect.getProductName(),
				"informix") || containsIgnoreCase(dialect.getSimpleName(), "informix");
		for (Throwable current = failure; current != null;
				current = current.getCause()) {
			if (current instanceof SQLException sqlException && (
					containsIgnoreCase(sqlException.getMessage(),
							"ISAM error: key value locked")
					|| informix && (sqlException.getErrorCode() == -244
							|| sqlException.getErrorCode() == -144
							|| sqlException.getErrorCode() == -107))) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsIgnoreCase(final String value,
			final String fragment) {
		return value != null && value.toLowerCase(Locale.ROOT)
				.contains(fragment.toLowerCase(Locale.ROOT));
	}

	private boolean isSybaseDeadlock(final SQLException failure) {
		return "Adaptive Server Enterprise"
				.equalsIgnoreCase(dialect.getProductName())
				&& failure.getErrorCode() == 1205;
	}

	private boolean isSpannerAbort(final SQLException failure) {
		final boolean spanner = containsIgnoreCase(dialect.getProductName(),
				"spanner") || containsIgnoreCase(dialect.getSimpleName(), "spanner");
		if (!spanner) {
			return false;
		}
		for (Throwable current = failure; current != null;
				current = current.getCause()) {
			if (containsIgnoreCase(current.getClass().getSimpleName(), "Aborted")
					|| containsIgnoreCase(current.getMessage(),
							"aborted due to a concurrent modification")) {
				return true;
			}
		}
		return false;
	}

	private boolean isSapHanaSerializationFailure(final SQLException failure) {
		final boolean hana = containsIgnoreCase(dialect.getProductName(), "hana")
				|| containsIgnoreCase(dialect.getSimpleName(), "hana");
		return hana && (failure.getErrorCode() == 138
				|| containsIgnoreCase(failure.getMessage(),
						"transaction serialization failure"));
	}

	private static boolean isUniqueViolation(final SQLException failure) {
		for (SQLException current = failure; current != null;
				current = current.getNextException()) {
			if (current instanceof SQLIntegrityConstraintViolationException
					|| "23505".equals(current.getSQLState())
					|| "23000".equals(current.getSQLState())) {
				return true;
			}
		}
		return false;
	}

	private void restoreConnection(final int isolation, final Throwable failure)
			throws SQLException {
		SQLException cleanupFailure = null;
		try {
			connection.setAutoCommit(true);
		} catch (SQLException e) {
			cleanupFailure = e;
		}
		try {
			connection.setTransactionIsolation(isolation);
		} catch (SQLException e) {
			if (cleanupFailure == null) {
				cleanupFailure = e;
			} else {
				cleanupFailure.addSuppressed(e);
			}
		}
		if (cleanupFailure != null) {
			if (failure != null) {
				failure.addSuppressed(cleanupFailure);
			} else {
				throw cleanupFailure;
			}
		}
	}

	private void ensureTable() throws SQLException {
		if (JdbcBulkMigrationJobLeaseTable.exists(connection, rawTableName)) {
			JdbcBulkMigrationJobLeaseTable.validateStructure(connection, rawTableName);
			return;
		}
		final Table table = JdbcBulkMigrationJobLeaseTable.table(rawTableName);
		final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
				.getSqlFactory(table, State.Added);
		new ConnectionSqlExecutor(connection, false).execute(factory.createSql(table));
		JdbcBulkMigrationJobLeaseTable.validateStructure(connection, rawTableName);
	}

	private static void validateCandidate(final BulkMigrationJobLease lease,
			final Instant now) {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(now, "now");
		if (lease.isExpiredAt(now)) {
			throw new IllegalArgumentException("lease must expire after now");
		}
	}

	@FunctionalInterface
	private interface SqlCallable<T> {
		T call() throws SQLException;
	}
}
