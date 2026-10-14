/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

/**
 * PostgreSQL 18 monitoring SELECT statements. Privileges, extension versions,
 * preload settings and NUMA build support are checked by the server at execution.
 * No query is executed by this builder.
 */
public class PostgresMonitoringBuilder {
	private final Dialect dialect;

	public PostgresMonitoringBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String asynchronousIo() {
		return select("pg_catalog.pg_aios");
	}

	public String backendIo(int processId) {
		return select("pg_catalog.pg_stat_get_backend_io(" + processId(processId) + ")");
	}

	public String backendWal(int processId) {
		return select("pg_catalog.pg_stat_get_backend_wal(" + processId(processId) + ")");
	}

	/** Explicitly resets the selected backend's counters; requires EXECUTE privilege. */
	public String resetBackendStats(int processId) {
		checkVersion();
		return "SELECT pg_catalog.pg_stat_reset_backend_stats(" + processId(processId) + ")";
	}

	/** Requires pg_stat_statements 1.12 (PostgreSQL 18) and shared preloading. */
	public String statementStatistics() {
		return statementStatistics(null);
	}

	/** @param extensionSchema optional schema in which pg_stat_statements is installed */
	public String statementStatistics(String extensionSchema) {
		checkVersion();
		String name = "pg_stat_statements";
		if (extensionSchema != null) {
			if (extensionSchema.isBlank()) {
				throw new IllegalArgumentException("extensionSchema must not be blank.");
			}
			name = dialect.quote(extensionSchema) + "." + name;
		}
		return "SELECT userid, dbid, toplevel, queryid, query, wal_records, wal_fpi, wal_bytes, "
				+ "wal_buffers_full, parallel_workers_to_launch, parallel_workers_launched FROM " + name;
	}

	public String loadedModules() {
		return select("pg_catalog.pg_get_loaded_modules()");
	}

	public String numaAvailable() {
		checkVersion();
		return "SELECT pg_catalog.pg_numa_available()";
	}

	public String sharedMemoryNuma() {
		return select("pg_catalog.pg_shmem_allocations_numa");
	}

	public String bufferCacheNuma() {
		return select("pg_buffercache_numa");
	}

	private String select(String source) {
		checkVersion();
		return "SELECT * FROM " + source;
	}

	private int processId(int value) {
		checkVersion();
		if (value <= 0) {
			throw new IllegalArgumentException("processId must be positive.");
		}
		return value;
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException("These monitoring operations require PostgreSQL 18 or later.");
		}
	}
}
