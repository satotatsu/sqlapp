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
 * SQL builder for operations provided by the {@code pg_buffercache} extension.
 */
public class PostgresBufferCacheBuilder {
	private final Dialect dialect;

	public PostgresBufferCacheBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	/**
	 * Evicts all unpinned buffers belonging to a relation.
	 */
	public String evictRelation(String relationName) {
		return evictRelation(null, relationName);
	}

	/**
	 * Evicts all unpinned buffers belonging to a relation.
	 */
	public String evictRelation(String schemaName, String relationName) {
		checkPostgres18();
		require(relationName, "relationName");
		StringBuilder relation = new StringBuilder();
		if (!CommonUtils.isEmpty(schemaName)) {
			relation.append(dialect.quote(schemaName)).append(".");
		}
		relation.append(dialect.quote(relationName));
		return "SELECT * FROM pg_buffercache_evict_relation("
				+ sqlString(relation.toString()) + "::regclass)";
	}

	/**
	 * Evicts every unpinned buffer from the shared buffer pool.
	 */
	public String evictAll() {
		checkPostgres18();
		return "SELECT * FROM pg_buffercache_evict_all()";
	}

	private void checkPostgres18() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Bulk pg_buffercache eviction requires PostgreSQL 18 or later.");
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
