/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * DuckDB capability boundaries; each version inherits its nearest supported
 * predecessor and overrides only the differences.
 */
public final class DuckDbVersions {

	private DuckDbVersions() {
	}

	/**
	 * DuckDB 1.1: named constraints and referenced tables in duckdb_constraints().
	 */
	public static class DuckDb11 extends DuckDb {
		private static final long serialVersionUID = 1L;

		protected DuckDb11(final Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsConstraintMetadata() {
			return true;
		}
	}

	/** DuckDB 1.2: ALTER TABLE ... ADD PRIMARY KEY. */
	public static class DuckDb12 extends DuckDb11 {
		private static final long serialVersionUID = 1L;

		protected DuckDb12(final Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsAlterTableAddPrimaryKey() {
			return true;
		}
	}

	/** DuckDB 1.4: MERGE INTO. Also used for DuckDB 1.5 and 2.x. */
	public static class DuckDb14 extends DuckDb12 {
		private static final long serialVersionUID = 1L;

		protected DuckDb14(final Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsMerge() {
			return true;
		}
	}
}
