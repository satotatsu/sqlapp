/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.transfer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import com.sqlapp.data.db.dialect.DialectResolver;

/** Connection settings shared by the transfer services. */
final class TransferConnections {

	private TransferConnections() {
	}

	/**
	 * Requests read-only mode for a phase that only reads.
	 * <p>
	 * JDBC defines read-only mode as a hint to the driver. Some drivers fix it when
	 * the database is opened and reject the request on a connection; DuckDB is one
	 * of them. The phases using this method issue no writes themselves, so such a
	 * driver is used without the hint. Any other failure is reported.
	 * </p>
	 *
	 * @return {@code true} when the driver accepted read-only mode
	 */
	static boolean requestReadOnly(final Connection connection) throws SQLException {
		try {
			connection.setReadOnly(true);
			return true;
		} catch (final SQLFeatureNotSupportedException e) {
			return false;
		}
	}

	/**
	 * Isolation level that gives verification a stable view of the database:
	 * SERIALIZABLE, or REPEATABLE READ where the dialect declares it to be snapshot
	 * isolation.
	 *
	 * @return the level, or {@code null} when the database offers neither
	 * @see com.sqlapp.data.db.dialect.Dialect#isRepeatableReadSnapshotIsolation()
	 */
	static Integer stableReadIsolation(final Connection connection) throws SQLException {
		final var metadata = connection.getMetaData();
		if (metadata.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)) {
			return Connection.TRANSACTION_SERIALIZABLE;
		}
		if (metadata.supportsTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ)
				&& DialectResolver.getInstance().getDialect(connection).isRepeatableReadSnapshotIsolation()) {
			return Connection.TRANSACTION_REPEATABLE_READ;
		}
		return null;
	}

	/**
	 * Switches the connection to {@link #stableReadIsolation} and checks that the
	 * driver kept it. Call before starting the reading transaction.
	 */
	static void useStableReadIsolation(final Connection connection) throws SQLException {
		final Integer level = stableReadIsolation(connection);
		if (level == null) {
			throw new IllegalArgumentException("Target transaction/verification isolation support is insufficient.");
		}
		connection.setTransactionIsolation(level);
		if (connection.getTransactionIsolation() != level) {
			throw new IllegalArgumentException("Target did not retain requested isolation.");
		}
	}
}
