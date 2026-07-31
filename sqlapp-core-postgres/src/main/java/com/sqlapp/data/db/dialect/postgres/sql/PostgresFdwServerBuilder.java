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
 * {@code CREATE SERVER ... FOREIGN DATA WRAPPER postgres_fdw} SQL builder.
 */
public class PostgresFdwServerBuilder {
	private static final Set<String> USER_MAPPING_OPTIONS =
			Set.of("user", "password", "sslpassword");

	private final Dialect dialect;
	private final String serverName;
	private final Map<String, String> options = new LinkedHashMap<>();
	private boolean useScramPassthrough;

	public PostgresFdwServerBuilder(Dialect dialect, String serverName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		this.serverName = require(serverName, "serverName");
	}

	public PostgresFdwServerBuilder option(String name, String value) {
		require(name, "optionName");
		Objects.requireNonNull(value, "optionValue");
		String normalized = name.toLowerCase(java.util.Locale.ROOT);
		if (USER_MAPPING_OPTIONS.contains(normalized)) {
			throw new IllegalArgumentException(
					name + " must be specified in a user mapping, not a foreign server.");
		}
		options.put(normalized, value);
		return this;
	}

	public PostgresFdwServerBuilder useScramPassthrough(boolean value) {
		this.useScramPassthrough = value;
		return this;
	}

	public String buildCreate(boolean ifNotExists) {
		if (useScramPassthrough
				&& dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"postgres_fdw use_scram_passthrough requires PostgreSQL 18 or later.");
		}
		StringBuilder builder = new StringBuilder("CREATE SERVER ");
		if (ifNotExists) {
			builder.append("IF NOT EXISTS ");
		}
		builder.append(dialect.quote(serverName))
				.append(" FOREIGN DATA WRAPPER postgres_fdw");
		Map<String, String> allOptions = new LinkedHashMap<>(options);
		if (useScramPassthrough) {
			allOptions.put("use_scram_passthrough", "true");
		}
		if (!allOptions.isEmpty()) {
			builder.append(" OPTIONS (");
			int index = 0;
			for (Map.Entry<String, String> entry : allOptions.entrySet()) {
				if (index++ > 0) {
					builder.append(", ");
				}
				builder.append(entry.getKey()).append(" ")
						.append(sqlString(entry.getValue()));
			}
			builder.append(")");
		}
		return builder.toString();
	}

	public String alterScramPassthrough(boolean value, boolean add) {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"postgres_fdw use_scram_passthrough requires PostgreSQL 18 or later.");
		}
		return "ALTER SERVER " + dialect.quote(serverName) + " OPTIONS ("
				+ (add ? "ADD" : "SET")
				+ " use_scram_passthrough " + sqlString(Boolean.toString(value))
				+ ")";
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
