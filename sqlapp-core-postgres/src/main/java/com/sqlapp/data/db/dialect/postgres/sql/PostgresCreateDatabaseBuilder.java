/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Catalog;

/**
 * Explicit database creation from a Schema catalog. Unlike Catalog CREATE,
 * which recreates contained objects, this creates the database itself.
 * Requires CREATEDB privileges and execution outside a transaction block.
 */
public class PostgresCreateDatabaseBuilder {
	private final Dialect dialect;
	private String strategy;
	private String template;

	public PostgresCreateDatabaseBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	/** Optional PostgreSQL 15+ strategy; omission preserves the server default. */
	public PostgresCreateDatabaseBuilder strategy(String value) {
		if (dialect.compareTo(DialectHolder.postgreSQL150) < 0) {
			throw new IllegalArgumentException("CREATE DATABASE STRATEGY requires PostgreSQL 15 or later.");
		}
		String normalized = require(value).toUpperCase(Locale.ROOT);
		if (!Set.of("FILE_COPY", "WAL_LOG").contains(normalized)) {
			throw new IllegalArgumentException("strategy must be FILE_COPY or WAL_LOG.");
		}
		strategy = normalized;
		return this;
	}

	public PostgresCreateDatabaseBuilder template(String name) {
		template = require(name);
		return this;
	}

	public String build(Catalog catalog) {
		Objects.requireNonNull(catalog, "catalog");
		StringBuilder sql = new StringBuilder("CREATE DATABASE ").append(dialect.quote(require(catalog.getName())));
		if (template != null) {
			sql.append(" TEMPLATE = ").append(dialect.quote(template));
		}
		if (catalog.getCharacterSet() != null) {
			sql.append(" ENCODING = '").append(require(catalog.getCharacterSet()).replace("'", "''")).append("'");
		}
		if (strategy != null) {
			sql.append(" STRATEGY = ").append(strategy);
		}
		return sql.toString();
	}

	private String require(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Database name, encoding or option must not be blank or contain NUL.");
		}
		return value;
	}
}
