/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * SQL builder for general-purpose functions introduced in recent PostgreSQL
 * versions.
 */
public class PostgresModernFunctionBuilder {
	private static final Set<String> INTEGER_TYPES = Set.of(
			"smallint", "integer", "bigint");

	private final Dialect dialect;

	public PostgresModernFunctionBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String casefold(String textExpression) {
		checkPostgres18();
		return unary("casefold", textExpression);
	}

	public String crc32(String byteaExpression) {
		checkPostgres18();
		return unary("crc32", byteaExpression);
	}

	public String crc32c(String byteaExpression) {
		checkPostgres18();
		return unary("crc32c", byteaExpression);
	}

	public String reverseBytes(String byteaExpression) {
		checkPostgres18();
		return unary("reverse", byteaExpression);
	}

	public String gamma(String numericExpression) {
		checkPostgres18();
		return unary("gamma", numericExpression);
	}

	public String lgamma(String numericExpression) {
		checkPostgres18();
		return unary("lgamma", numericExpression);
	}

	public String integerToBytea(String integerExpression) {
		checkPostgres18();
		return cast(integerExpression, "bytea");
	}

	public String byteaToInteger(String byteaExpression, String integerType) {
		checkPostgres18();
		require(integerType, "integerType");
		String normalized = integerType.toLowerCase(Locale.ROOT);
		if (!INTEGER_TYPES.contains(normalized)) {
			throw new IllegalArgumentException(
					"integerType must be smallint, integer, or bigint.");
		}
		return cast(byteaExpression, normalized);
	}

	/**
	 * PostgreSQL 18 form that can also remove null array elements.
	 */
	public String jsonStripNulls(String jsonExpression, boolean stripInArrays) {
		checkPostgres18();
		require(jsonExpression, "jsonExpression");
		return "json_strip_nulls(" + jsonExpression + ", " + stripInArrays + ")";
	}

	/**
	 * PostgreSQL 18 form that can also remove null array elements.
	 */
	public String jsonbStripNulls(String jsonbExpression, boolean stripInArrays) {
		checkPostgres18();
		require(jsonbExpression, "jsonbExpression");
		return "jsonb_strip_nulls(" + jsonbExpression + ", " + stripInArrays + ")";
	}

	private String unary(String function, String expression) {
		require(expression, "expression");
		return function + "(" + expression + ")";
	}

	private String cast(String expression, String type) {
		require(expression, "expression");
		return "CAST(" + expression + " AS " + type + ")";
	}

	private void checkPostgres18() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"These functions require PostgreSQL 18 or later.");
		}
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
