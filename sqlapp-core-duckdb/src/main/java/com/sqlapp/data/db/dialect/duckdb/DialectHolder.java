/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * DuckDB dialect instances ordered by version.
 */
public class DialectHolder {
	public final static Dialect duckdb14 = new DuckDbVersions.DuckDb14(() -> null);
	public final static Dialect duckdb12 = new DuckDbVersions.DuckDb12(() -> duckdb14);
	public final static Dialect duckdb11 = new DuckDbVersions.DuckDb11(() -> duckdb12);
	public final static Dialect duckdb10 = new DuckDb(() -> duckdb11);
	/**
	 * Unversioned callers retain the newest capabilities. Resolve a connection
	 * for a concrete target.
	 */
	public final static Dialect defaultDialect = duckdb14;
}
