/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Serializable-transaction lease store backed by a target control table. */
public final class JdbcBulkMigrationJobLeaseStore
		implements BulkMigrationJobLeaseStore {
	public static final String DEFAULT_TABLE_NAME = "sqlapp_bulk_job_lease";

	private final Connection connection;
	private final String rawTableName;
	private final String tableName;
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
		this.tableName = dialect.quote(tableName);
		final Table table = leaseTable();
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
		validateFingerprint(planFingerprint);
		return loadInternal(planFingerprint);
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now)
			throws SQLException {
		validateCandidate(lease, now);
		return transaction(() -> {
			final BulkMigrationJobLease current = loadInternal(lease.planFingerprint())
					.orElse(null);
			if (current != null && !current.isExpiredAt(now)) {
				return false;
			}
			write(lease, current == null ? SqlType.INSERT : SqlType.UPDATE);
			return true;
		});
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
						parameters(planFingerprint));
			}
			return null;
		});
	}

	private Optional<BulkMigrationJobLease> loadInternal(final String fingerprint)
			throws SQLException {
		final BulkMigrationJobLease[] result = new BulkMigrationJobLease[1];
		new JdbcHandler(sqlNodes.get(SqlType.SELECT),
				rs -> result[0] = lease(rs, fingerprint))
				.execute(connection, parameters(fingerprint));
		return Optional.ofNullable(result[0]);
	}

	private static BulkMigrationJobLease lease(final ExResultSet resultSet,
			final String fingerprint) throws SQLException {
		final Map<String, Integer> columns = new LinkedHashMap<>();
		final var metadata = resultSet.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			columns.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
		}
		return new BulkMigrationJobLease(fingerprint,
				resultSet.getString(columns.get("OWNER_ID")),
				Instant.parse(resultSet.getString(columns.get("EXPIRES_AT"))));
	}

	private void write(final BulkMigrationJobLease lease, final SqlType type)
			throws SQLException {
		new JdbcHandler(sqlNodes.get(type)).execute(connection, parameters(lease));
	}

	private <T> T transaction(final SqlCallable<T> callable) throws SQLException {
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
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT " + column("PLAN_FINGERPRINT") + ", "
					+ column("OWNER_ID") + ", " + column("EXPIRES_AT") + " FROM "
					+ tableName + " WHERE 1 = 0").close();
		} catch (SQLException missing) {
			try {
				final Table table = leaseTable();
				final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(table, State.Added);
				new ConnectionSqlExecutor(connection, false)
						.execute(factory.createSql(table));
			} catch (SQLException createFailure) {
				createFailure.addSuppressed(missing);
				throw createFailure;
			}
		}
	}

	private Table leaseTable() {
		final Table table = new Table(rawTableName);
		final Column fingerprint = varchar("PLAN_FINGERPRINT");
		table.getColumns().add(fingerprint);
		table.getColumns().add(varchar("OWNER_ID"));
		table.getColumns().add(new Column("EXPIRES_AT").setDataType(DataType.VARCHAR)
				.setLength(40).setNotNull(true));
		table.setPrimaryKey((String) null, fingerprint);
		return table;
	}

	private static Column varchar(final String name) {
		return new Column(name).setDataType(DataType.VARCHAR)
				.setLength(BulkMigrationJobLease.ID_MAX_LENGTH).setNotNull(true);
	}

	private String column(final String name) {
		return dialect.quote(name);
	}

	private static ParametersContext parameters(final String fingerprint) {
		final ParametersContext parameters = new ParametersContext();
		parameters.put("PLAN_FINGERPRINT", fingerprint);
		return parameters;
	}

	private static ParametersContext parameters(final BulkMigrationJobLease lease) {
		final ParametersContext parameters = parameters(lease.planFingerprint());
		parameters.put("OWNER_ID", lease.ownerId());
		parameters.put("EXPIRES_AT", lease.expiresAt().toString());
		return parameters;
	}

	private static void validateFingerprint(final String fingerprint) {
		new BulkMigrationJobLease(fingerprint, "validation", Instant.MAX);
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
