/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 {@code CREATE FOREIGN TABLE ... (LIKE ...)} builder.
 */
public class PostgresForeignTableLikeBuilder {
	private static final Set<String> LIKE_OPTIONS = Set.of(
			"COMMENTS", "CONSTRAINTS", "DEFAULTS", "GENERATED", "STATISTICS",
			"ALL");

	private final Dialect dialect;
	private final String tableName;
	private String schemaName;
	private String sourceTableName;
	private String sourceSchemaName;
	private String serverName;
	private boolean ifNotExists;
	private final List<String> likeOptions = new ArrayList<>();
	private final List<String> foreignOptions = new ArrayList<>();

	public PostgresForeignTableLikeBuilder(Dialect dialect, String tableName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		require(tableName, "tableName");
		this.tableName = tableName;
	}

	public PostgresForeignTableLikeBuilder schema(String value) {
		this.schemaName = value;
		return this;
	}

	public PostgresForeignTableLikeBuilder like(String sourceTable) {
		return like(null, sourceTable);
	}

	public PostgresForeignTableLikeBuilder like(String sourceSchema,
			String sourceTable) {
		require(sourceTable, "sourceTable");
		this.sourceSchemaName = sourceSchema;
		this.sourceTableName = sourceTable;
		return this;
	}

	public PostgresForeignTableLikeBuilder including(String value) {
		return likeOption("INCLUDING", value);
	}

	public PostgresForeignTableLikeBuilder excluding(String value) {
		return likeOption("EXCLUDING", value);
	}

	public PostgresForeignTableLikeBuilder server(String value) {
		require(value, "server");
		this.serverName = value;
		return this;
	}

	public PostgresForeignTableLikeBuilder option(String name, String value) {
		require(name, "option name");
		Objects.requireNonNull(value, "option value");
		foreignOptions.add(dialect.quote(name) + " " + sqlString(value));
		return this;
	}

	public PostgresForeignTableLikeBuilder ifNotExists(boolean value) {
		this.ifNotExists = value;
		return this;
	}

	public String build() {
		checkVersion();
		require(sourceTableName, "sourceTable");
		require(serverName, "server");
		StringBuilder builder = new StringBuilder("CREATE FOREIGN TABLE ");
		if (ifNotExists) {
			builder.append("IF NOT EXISTS ");
		}
		appendQualifiedName(builder, schemaName, tableName);
		builder.append(" (LIKE ");
		appendQualifiedName(builder, sourceSchemaName, sourceTableName);
		for (String option : likeOptions) {
			builder.append(" ").append(option);
		}
		builder.append(") SERVER ").append(dialect.quote(serverName));
		if (!foreignOptions.isEmpty()) {
			builder.append(" OPTIONS (")
					.append(String.join(", ", foreignOptions)).append(")");
		}
		return builder.toString();
	}

	private PostgresForeignTableLikeBuilder likeOption(String mode,
			String value) {
		require(value, "like option");
		String normalized = value.toUpperCase(java.util.Locale.ROOT);
		if (!LIKE_OPTIONS.contains(normalized)) {
			throw new IllegalArgumentException("Unsupported LIKE option: " + value);
		}
		likeOptions.add(mode + " " + normalized);
		return this;
	}

	private void appendQualifiedName(StringBuilder builder, String schema,
			String name) {
		if (!CommonUtils.isEmpty(schema)) {
			builder.append(dialect.quote(schema)).append(".");
		}
		builder.append(dialect.quote(name));
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"CREATE FOREIGN TABLE LIKE requires PostgreSQL 18 or later.");
		}
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
