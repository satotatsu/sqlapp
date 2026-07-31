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
 * {@code dblink_fdw} foreign-server SQL builder.
 */
public class PostgresDblinkServerBuilder {
	private static final Set<String> USER_MAPPING_OPTIONS =
			Set.of("user", "password");

	private final Dialect dialect;
	private final String serverName;
	private final Map<String, String> options = new LinkedHashMap<>();
	private boolean useScramPassthrough;

	public PostgresDblinkServerBuilder(Dialect dialect, String serverName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		this.serverName = require(serverName, "serverName");
	}

	public PostgresDblinkServerBuilder option(String name, String value) {
		String normalized = require(name, "optionName")
				.toLowerCase(Locale.ROOT);
		if (USER_MAPPING_OPTIONS.contains(normalized)) {
			throw new IllegalArgumentException(
					name + " must be specified in a user mapping.");
		}
		if ("use_scram_passthrough".equals(normalized)) {
			throw new IllegalArgumentException(
					"use_scram_passthrough must be set using its dedicated method.");
		}
		options.put(normalized,
				Objects.requireNonNull(value, "optionValue"));
		return this;
	}

	public PostgresDblinkServerBuilder useScramPassthrough(boolean value) {
		this.useScramPassthrough = value;
		return this;
	}

	public String buildCreate(boolean ifNotExists) {
		checkVersion();
		StringBuilder builder = new StringBuilder("CREATE SERVER ");
		if (ifNotExists) {
			builder.append("IF NOT EXISTS ");
		}
		builder.append(dialect.quote(serverName))
				.append(" FOREIGN DATA WRAPPER dblink_fdw");
		Map<String, String> all = new LinkedHashMap<>(options);
		if (useScramPassthrough) {
			all.put("use_scram_passthrough", "true");
		}
		appendOptions(builder, all, null);
		return builder.toString();
	}

	public String alterScramPassthrough(boolean value, boolean add) {
		checkPostgres18();
		StringBuilder builder = new StringBuilder("ALTER SERVER ")
				.append(dialect.quote(serverName));
		Map<String, String> option = Map.of(
				"use_scram_passthrough", Boolean.toString(value));
		appendOptions(builder, option, add ? "ADD" : "SET");
		return builder.toString();
	}

	private void appendOptions(StringBuilder builder,
			Map<String, String> values, String action) {
		if (values.isEmpty()) {
			return;
		}
		builder.append(" OPTIONS (");
		int index = 0;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (index++ > 0) {
				builder.append(", ");
			}
			if (action != null) {
				builder.append(action).append(" ");
			}
			builder.append(entry.getKey()).append(" ")
					.append(sqlString(entry.getValue()));
		}
		builder.append(")");
	}

	private void checkVersion() {
		if (useScramPassthrough) {
			checkPostgres18();
		}
	}

	private void checkPostgres18() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"dblink_fdw use_scram_passthrough requires PostgreSQL 18 or later.");
		}
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
