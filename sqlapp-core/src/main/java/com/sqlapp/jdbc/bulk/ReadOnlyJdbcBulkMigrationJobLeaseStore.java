/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.DialectResolver;

/** JDBC lease reader that never creates, updates, or deletes its control table. */
public final class ReadOnlyJdbcBulkMigrationJobLeaseStore
		implements BulkMigrationJobLeaseStore {
	private final Connection connection;
	private final String rawTableName;
	private final String tableName;
	private final String fingerprintColumn;
	private final String ownerColumn;
	private final String expiresColumn;

	public ReadOnlyJdbcBulkMigrationJobLeaseStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid lease table name: " + tableName);
		}
		final var dialect = DialectResolver.getInstance().getDialect(connection);
		this.rawTableName = tableName;
		this.tableName = dialect.quote(tableName);
		this.fingerprintColumn = dialect.quote("PLAN_FINGERPRINT");
		this.ownerColumn = dialect.quote("OWNER_ID");
		this.expiresColumn = dialect.quote("EXPIRES_AT");
	}

	@Override
	public Optional<BulkMigrationJobLease> load(final String planFingerprint)
			throws SQLException {
		new BulkMigrationJobLease(planFingerprint, "validation", Instant.MAX);
		if (!tableExists()) {
			return Optional.empty();
		}
		final String sql = "SELECT " + ownerColumn + ", " + expiresColumn + " FROM "
				+ tableName + " WHERE " + fingerprintColumn + " = ?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, planFingerprint);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(new BulkMigrationJobLease(planFingerprint,
						resultSet.getString("OWNER_ID"),
						Instant.parse(resultSet.getString("EXPIRES_AT"))));
			}
		}
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now) {
		throw new UnsupportedOperationException("Read-only lease store");
	}

	@Override
	public boolean renew(final BulkMigrationJobLease lease, final Instant now) {
		throw new UnsupportedOperationException("Read-only lease store");
	}

	@Override
	public void release(final String planFingerprint, final String ownerId) {
		throw new UnsupportedOperationException("Read-only lease store");
	}

	private boolean tableExists() throws SQLException {
		try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(),
				null, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				if (rawTableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}
}
