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
 * PostgreSQL 18 {@code PG_UNICODE_FAST} builtin collation SQL builder.
 */
public class PostgresUnicodeFastCollationBuilder {
	private final Dialect dialect;

	public PostgresUnicodeFastCollationBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String create(String name) {
		return create(null, name, false);
	}

	public String create(String schemaName, String name,
			boolean ifNotExists) {
		checkVersion();
		StringBuilder builder = new StringBuilder("CREATE COLLATION ");
		if (ifNotExists) {
			builder.append("IF NOT EXISTS ");
		}
		appendName(builder, schemaName, name);
		return builder.append(
				" (PROVIDER = builtin, LOCALE = 'PG_UNICODE_FAST')")
				.toString();
	}

	public String drop(String schemaName, String name, boolean ifExists,
			boolean cascade) {
		checkVersion();
		StringBuilder builder = new StringBuilder("DROP COLLATION ");
		if (ifExists) {
			builder.append("IF EXISTS ");
		}
		appendName(builder, schemaName, name);
		if (cascade) {
			builder.append(" CASCADE");
		}
		return builder.toString();
	}

	private void appendName(StringBuilder builder, String schemaName,
			String name) {
		if (!CommonUtils.isEmpty(schemaName)) {
			builder.append(dialect.quote(schemaName)).append(".");
		}
		builder.append(dialect.quote(require(name, "name")));
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"PG_UNICODE_FAST requires PostgreSQL 18 or later.");
		}
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
