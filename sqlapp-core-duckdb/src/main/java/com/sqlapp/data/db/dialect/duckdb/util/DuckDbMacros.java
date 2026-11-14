/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Builds {@code CREATE MACRO} statements from the parts exposed by
 * {@code duckdb_functions()}.
 */
public final class DuckDbMacros {

	private DuckDbMacros() {
	}

	/**
	 * One macro overload.
	 *
	 * @param parameterNames parameter names
	 * @param parameterTypes parameter types (elements may be {@code null})
	 * @param body           macro body (expression or query)
	 */
	public record Overload(List<String> parameterNames, List<String> parameterTypes, String body) {
	}

	/**
	 * Creates a complete {@code CREATE MACRO} statement.
	 *
	 * @param dialect   dialect used for quoting
	 * @param schema    schema name or {@code null}
	 * @param name      macro name
	 * @param table     {@code true} for a table macro
	 * @param overloads overloads (at least one)
	 * @return statement without terminator
	 */
	public static String createMacro(final Dialect dialect, final String schema, final String name, final boolean table,
			final List<Overload> overloads) {
		if (overloads.isEmpty()) {
			throw new IllegalArgumentException("DuckDB macro requires at least one overload: " + name);
		}
		final StringBuilder builder = new StringBuilder("CREATE MACRO ");
		if (schema != null && !schema.isEmpty()) {
			builder.append(dialect.quote(schema)).append('.');
		}
		builder.append(dialect.quote(name));
		for (int i = 0; i < overloads.size(); i++) {
			final Overload overload = overloads.get(i);
			if (overload.body() == null || overload.body().isBlank()) {
				throw new IllegalArgumentException("DuckDB macro body is unavailable: " + name);
			}
			if (i > 0) {
				builder.append(',');
			}
			builder.append('(');
			for (int j = 0; j < overload.parameterNames().size(); j++) {
				if (j > 0) {
					builder.append(", ");
				}
				builder.append(dialect.quote(overload.parameterNames().get(j)));
				final String type = j < overload.parameterTypes().size() ? overload.parameterTypes().get(j) : null;
				if (type != null && !type.isEmpty()) {
					builder.append(' ').append(type);
				}
			}
			builder.append(") AS ");
			if (table) {
				builder.append("TABLE ");
			}
			builder.append(overload.body().strip());
		}
		return builder.toString();
	}
}
