/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL {@code EXPLAIN} SQL builder.
 */
public class PostgresExplainBuilder {
	private static final Set<String> FORMATS =
			Set.of("TEXT", "XML", "JSON", "YAML");
	private static final Set<String> SERIALIZE_MODES =
			Set.of("NONE", "TEXT", "BINARY");

	private final Dialect dialect;
	private final Map<String, String> options = new LinkedHashMap<>();
	private String statement;

	public PostgresExplainBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public PostgresExplainBuilder statement(String value) {
		this.statement = require(value, "statement");
		return this;
	}

	public PostgresExplainBuilder analyze(boolean value) {
		return option("ANALYZE", value);
	}

	public PostgresExplainBuilder verbose(boolean value) {
		return option("VERBOSE", value);
	}

	public PostgresExplainBuilder costs(boolean value) {
		return option("COSTS", value);
	}

	public PostgresExplainBuilder settings(boolean value) {
		return option("SETTINGS", value);
	}

	public PostgresExplainBuilder genericPlan(boolean value) {
		if (dialect.compareTo(DialectHolder.postgreSQL160) < 0) {
			throw new IllegalArgumentException(
					"EXPLAIN GENERIC_PLAN requires PostgreSQL 16 or later.");
		}
		return option("GENERIC_PLAN", value);
	}

	public PostgresExplainBuilder buffers(boolean value) {
		return option("BUFFERS", value);
	}

	public PostgresExplainBuilder serialize(String value) {
		checkPostgres17("SERIALIZE");
		return enumOption("SERIALIZE", value, SERIALIZE_MODES);
	}

	public PostgresExplainBuilder wal(boolean value) {
		return option("WAL", value);
	}

	public PostgresExplainBuilder timing(boolean value) {
		return option("TIMING", value);
	}

	public PostgresExplainBuilder summary(boolean value) {
		return option("SUMMARY", value);
	}

	public PostgresExplainBuilder memory(boolean value) {
		checkPostgres17("MEMORY");
		return option("MEMORY", value);
	}

	public PostgresExplainBuilder format(String value) {
		return enumOption("FORMAT", value, FORMATS);
	}

	public String build() {
		require(statement, "statement");
		boolean analyze = Boolean.parseBoolean(options.get("ANALYZE"));
		boolean genericPlan = Boolean.parseBoolean(options.get("GENERIC_PLAN"));
		if (analyze && genericPlan) {
			throw new IllegalArgumentException(
					"GENERIC_PLAN cannot be used with ANALYZE.");
		}
		requireAnalyze(analyze, "WAL");
		requireAnalyze(analyze, "TIMING");
		if (options.containsKey("SERIALIZE")
				&& !"NONE".equals(options.get("SERIALIZE"))) {
			requireAnalyze(analyze, "SERIALIZE");
		}
		StringBuilder builder = new StringBuilder("EXPLAIN");
		if (!options.isEmpty()) {
			builder.append(" (");
			boolean first = true;
			for (Map.Entry<String, String> entry : options.entrySet()) {
				if (!first) {
					builder.append(", ");
				}
				builder.append(entry.getKey()).append(" ")
						.append(entry.getValue());
				first = false;
			}
			builder.append(")");
		}
		return builder.append(" ").append(statement).toString();
	}

	private PostgresExplainBuilder option(String name, boolean value) {
		options.put(name, Boolean.toString(value).toUpperCase(Locale.ROOT));
		return this;
	}

	private PostgresExplainBuilder enumOption(String name, String value,
			Set<String> allowed) {
		require(value, name);
		String normalized = value.toUpperCase(Locale.ROOT);
		if (!allowed.contains(normalized)) {
			throw new IllegalArgumentException(
					"Unsupported EXPLAIN " + name + ": " + value);
		}
		options.put(name, normalized);
		return this;
	}

	private void requireAnalyze(boolean analyze, String option) {
		if (options.containsKey(option)
				&& !"FALSE".equals(options.get(option)) && !analyze) {
			throw new IllegalArgumentException(
					option + " requires EXPLAIN ANALYZE.");
		}
	}

	private void checkPostgres17(String option) {
		if (dialect.compareTo(DialectHolder.postgreSQL170) < 0) {
			throw new IllegalArgumentException(
					"EXPLAIN " + option + " requires PostgreSQL 17 or later.");
		}
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
