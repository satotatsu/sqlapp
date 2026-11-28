/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import io.github.spannm.jackcess.Database;
import net.ucanaccess.jdbc.UcanaccessConnection;

/**
 * Access to the connection-owned file; readers must never close this handle.
 */
final class MdbMetadataSupport {

	/**
	 * Database reloaded once for a bulk metadata read. reloadDbIO reopens the
	 * Access file, so each reader must not repeat it within the same read.
	 */
	private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

	private record Scope(Connection connection, Database database) {
	}

	private MdbMetadataSupport() {
	}

	/** Runs readers against one reloaded Database for the given connection. */
	static void withDatabase(final Connection connection, final Database database, final Runnable action) {
		final Scope previous = CURRENT.get();
		CURRENT.set(new Scope(connection, database));
		try {
			action.run();
		} finally {
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}

	static Database database(final Connection connection) throws SQLException, IOException {
		final Scope scope = CURRENT.get();
		if (scope != null && scope.connection() == connection) {
			return scope.database();
		}
		final UcanaccessConnection access;
		// UCanAccess delegates Wrapper methods to HSQLDB, even for its own class.
		if (connection instanceof UcanaccessConnection direct) {
			access = direct;
		} else if (connection.isWrapperFor(UcanaccessConnection.class)) {
			access = connection.unwrap(UcanaccessConnection.class);
		} else {
			return null;
		}
		access.reloadDbIO();
		return access.getDbIO();
	}

	static Collection<String> tableNames(final Database database, final String tableName) throws IOException {
		return tableName == null || tableName.isEmpty() ? database.getTableNames() : List.of(tableName);
	}

	static boolean matchesName(final String requestedName, final String actualName) {
		return requestedName == null || requestedName.isEmpty() || requestedName.equalsIgnoreCase(actualName);
	}
}
