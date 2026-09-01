/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;

/** Portable JDBC isolation choices for post-migration verification. */
public enum BulkMigrationVerificationIsolation {
	DEFAULT(null),
	READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
	REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
	SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

	private final Integer jdbcLevel;

	BulkMigrationVerificationIsolation(final Integer jdbcLevel) {
		this.jdbcLevel = jdbcLevel;
	}

	public Integer getJdbcLevel() {
		return jdbcLevel;
	}
}
