/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import org.junit.jupiter.api.Test;

class TransferConnectionsTest {

	private static Connection rejecting(final SQLException failure) {
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					if ("setReadOnly".equals(method.getName())) {
						throw failure;
					}
					throw new UnsupportedOperationException(method.getName());
				});
	}

	@Test
	void treatsReadOnlyAsAHint() throws Exception {
		try (var connection = java.sql.DriverManager.getConnection("jdbc:hsqldb:mem:read_only_hint", "SA", "")) {
			assertTrue(TransferConnections.requestReadOnly(connection));
			assertTrue(connection.isReadOnly());
		}
		// DuckDB fixes read-only mode when the database is opened.
		try (var connection = java.sql.DriverManager.getConnection("jdbc:duckdb:")) {
			assertFalse(TransferConnections.requestReadOnly(connection));
		}
		assertFalse(TransferConnections.requestReadOnly(rejecting(new SQLFeatureNotSupportedException("fixed"))));
		assertThrows(SQLException.class,
				() -> TransferConnections.requestReadOnly(rejecting(new SQLException("down"))));
	}

	@Test
	void selectsAnIsolationLevelThatGivesStableReads() throws Exception {
		try (var connection = java.sql.DriverManager.getConnection("jdbc:hsqldb:mem:stable_reads", "SA", "")) {
			assertEquals(Connection.TRANSACTION_SERIALIZABLE, TransferConnections.stableReadIsolation(connection));
			TransferConnections.useStableReadIsolation(connection);
			assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
		}
		// DuckDB offers no SERIALIZABLE through JDBC; its REPEATABLE READ is snapshot
		// isolation.
		try (var connection = java.sql.DriverManager.getConnection("jdbc:duckdb:")) {
			assertEquals(Connection.TRANSACTION_REPEATABLE_READ, TransferConnections.stableReadIsolation(connection));
			TransferConnections.useStableReadIsolation(connection);
			assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
		}
	}
}
