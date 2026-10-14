/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

/**
 * Session-scoped PostgreSQL 18 extension settings; server privileges still
 * apply.
 */
public class PostgresExtensionSettingsBuilder {
	private final Dialect dialect;

	public PostgresExtensionSettingsBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	/**
	 * Requires passwordcheck to be loaded. The length is measured in bytes, not
	 * characters.
	 */
	public String passwordMinimumLength(int bytes) {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException("passwordcheck.min_password_length requires PostgreSQL 18 or later.");
		}
		if (bytes < 0) {
			throw new IllegalArgumentException("bytes must not be negative.");
		}
		return "SET passwordcheck.min_password_length = " + bytes;
	}
}
