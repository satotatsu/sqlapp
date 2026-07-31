/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL {@code postgres_fdw} connection inspection and management SQL.
 */
public class PostgresFdwConnectionBuilder {
	private final Dialect dialect;

	public PostgresFdwConnectionBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	/**
	 * Returns the PostgreSQL 18 connection status columns.
	 */
	public String listConnections(boolean checkConnection) {
		checkPostgres18();
		return "SELECT server_name, user_name, valid, used_in_xact, closed, "
				+ "remote_backend_pid FROM postgres_fdw_get_connections("
				+ checkConnection + ")";
	}

	public String disconnect(String serverName) {
		require(serverName, "serverName");
		return "SELECT postgres_fdw_disconnect("
				+ sqlString(serverName) + ")";
	}

	public String disconnectAll() {
		return "SELECT postgres_fdw_disconnect_all()";
	}

	private void checkPostgres18() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Detailed postgres_fdw connection status requires PostgreSQL 18 or later.");
		}
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
