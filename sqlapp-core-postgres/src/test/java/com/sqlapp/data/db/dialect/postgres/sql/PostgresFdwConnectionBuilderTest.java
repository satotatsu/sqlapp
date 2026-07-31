package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresFdwConnectionBuilderTest {

	@Test
	void testListDetailedConnections() {
		assertEquals(
				"SELECT server_name, user_name, valid, used_in_xact, closed, remote_backend_pid FROM postgres_fdw_get_connections(true)",
				new PostgresFdwConnectionBuilder(
						DialectHolder.postgreSQL180)
						.listConnections(true));
	}

	@Test
	void testRejectDetailedConnectionsBeforePostgres18() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFdwConnectionBuilder(
						DialectHolder.postgreSQL170)
						.listConnections(false));
	}

	@Test
	void testDisconnectServer() {
		assertEquals(
				"SELECT postgres_fdw_disconnect('reporting''server')",
				new PostgresFdwConnectionBuilder(
						DialectHolder.postgreSQL170)
						.disconnect("reporting'server"));
	}

	@Test
	void testDisconnectAllServers() {
		assertEquals(
				"SELECT postgres_fdw_disconnect_all()",
				new PostgresFdwConnectionBuilder(
						DialectHolder.postgreSQL170)
						.disconnectAll());
	}
}
