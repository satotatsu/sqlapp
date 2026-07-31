/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 named-argument calls for regular-expression functions.
 */
public class PostgresRegexpFunctionBuilder {
	public enum Function {
		MATCH("regexp_match", Set.of("string", "pattern", "flags")),
		MATCHES("regexp_matches", Set.of("string", "pattern", "flags")),
		LIKE("regexp_like", Set.of("string", "pattern", "flags")),
		REPLACE("regexp_replace",
				Set.of("string", "pattern", "replacement", "start", "N", "flags")),
		COUNT("regexp_count", Set.of("string", "pattern", "start", "flags")),
		INSTR("regexp_instr",
				Set.of("string", "pattern", "start", "N", "endoption", "flags",
						"subexpr")),
		SUBSTR("regexp_substr",
				Set.of("string", "pattern", "start", "N", "flags", "subexpr")),
		SPLIT_TO_TABLE("regexp_split_to_table",
				Set.of("string", "pattern", "flags")),
		SPLIT_TO_ARRAY("regexp_split_to_array",
				Set.of("string", "pattern", "flags"));

		private final String sqlName;
		private final Set<String> arguments;

		Function(String sqlName, Set<String> arguments) {
			this.sqlName = sqlName;
			this.arguments = arguments;
		}
	}

	private final Dialect dialect;
	private final Function function;
	private final Map<String, String> arguments = new LinkedHashMap<>();

	public PostgresRegexpFunctionBuilder(Dialect dialect, Function function) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		this.function = Objects.requireNonNull(function, "function");
	}

	/**
	 * Adds a raw SQL expression as a named function argument.
	 */
	public PostgresRegexpFunctionBuilder argument(String name,
			String expression) {
		require(name, "name");
		require(expression, "expression");
		if (!function.arguments.contains(name)) {
			throw new IllegalArgumentException(
					"Unsupported argument for " + function.sqlName + ": " + name);
		}
		arguments.put(name, expression);
		return this;
	}

	public String build() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Named arguments for regular-expression functions require PostgreSQL 18 or later.");
		}
		requireArgument("string");
		requireArgument("pattern");
		if (function == Function.REPLACE) {
			requireArgument("replacement");
		}
		StringBuilder builder = new StringBuilder(function.sqlName).append("(");
		int index = 0;
		for (Map.Entry<String, String> entry : arguments.entrySet()) {
			if (index++ > 0) {
				builder.append(", ");
			}
			builder.append("N".equals(entry.getKey()) ? "\"N\"" : entry.getKey())
					.append(" => ").append(entry.getValue());
		}
		return builder.append(")").toString();
	}

	private void requireArgument(String name) {
		if (!arguments.containsKey(name)) {
			throw new IllegalArgumentException(
					function.sqlName + " requires argument: " + name);
		}
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
