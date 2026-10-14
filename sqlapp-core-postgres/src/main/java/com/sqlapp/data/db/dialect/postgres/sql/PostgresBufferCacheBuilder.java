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
	 * Creates a PostgreSQL 18 single-buffer eviction statement returning both
	 * {@code buffer_evicted} and {@code buffer_flushed}. Requires the
	 * {@code pg_buffercache} extension and superuser privileges at execution time.
	 * Intended for developer testing; concurrent activity can change the buffer
	 * state immediately after execution.
	 *
	 * @param bufferId positive buffer identifier from {@code pg_buffercache.bufferid}
	 * @return SELECT statement exposing the eviction and flush status columns
	 */
	public String evict(int bufferId) {
		checkPostgres18();
		if (bufferId <= 0) {
			throw new IllegalArgumentException("bufferId must be positive.");
		}
		return "SELECT buffer_evicted, buffer_flushed FROM pg_buffercache_evict(" + bufferId + ")";
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
		return "SELECT * FROM pg_buffercache_evict_relation(" + sqlString(relation.toString()) + "::regclass)";
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
			throw new IllegalArgumentException("pg_buffercache eviction requires PostgreSQL 18 or later.");
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
