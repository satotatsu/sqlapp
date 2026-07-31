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
 * PostgreSQL 18 UUID function SQL builder.
 * <p>
 * Arguments are SQL expressions. Callers are responsible for binding or
 * otherwise safely constructing runtime values.
 */
public class PostgresUuidBuilder {
	private final Dialect dialect;

	public PostgresUuidBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String uuidV4() {
		checkVersion();
		return "uuidv4()";
	}

	public String uuidV7() {
		checkVersion();
		return "uuidv7()";
	}

	/**
	 * Generates a UUIDv7 with an interval-valued SQL shift expression.
	 */
	public String uuidV7(String shiftExpression) {
		checkVersion();
		require(shiftExpression, "shiftExpression");
		return "uuidv7(" + shiftExpression + ")";
	}

	public String extractVersion(String uuidExpression) {
		checkVersion();
		require(uuidExpression, "uuidExpression");
		return "uuid_extract_version(" + uuidExpression + ")";
	}

	public String extractTimestamp(String uuidExpression) {
		checkVersion();
		require(uuidExpression, "uuidExpression");
		return "uuid_extract_timestamp(" + uuidExpression + ")";
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"UUIDv4, UUIDv7 and UUID extraction functions require PostgreSQL 18 or later.");
		}
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
