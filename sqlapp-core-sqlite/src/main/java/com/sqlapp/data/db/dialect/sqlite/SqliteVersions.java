/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/**
 * Capability boundaries; each version inherits its nearest supported
 * predecessor.
 */
public final class SqliteVersions {
	private SqliteVersions() {
	}

	public static class Sqlite376 extends Sqlite {
		private static final long serialVersionUID = 1L;

		protected Sqlite376(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsWalCheckpointModes() {
			return true;
		}
	}

	public static class Sqlite3716 extends Sqlite376 {
		private static final long serialVersionUID = 1L;

		protected Sqlite3716(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsForeignKeyCheck() {
			return true;
		}
	}

	public static class Sqlite380 extends Sqlite3716 {
		private static final long serialVersionUID = 1L;

		protected Sqlite380(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsPartialIndexes() {
			return true;
		}
	}

	public static class Sqlite382 extends Sqlite380 {
		private static final long serialVersionUID = 1L;

		protected Sqlite382(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsWithoutRowid() {
			return true;
		}
	}

	public static class Sqlite383 extends Sqlite382 {
		private static final long serialVersionUID = 1L;

		protected Sqlite383(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsWith() {
			return true;
		}

		@Override
		public boolean supportsWithRecursive() {
			return true;
		}
	}

	public static class Sqlite388 extends Sqlite383 {
		private static final long serialVersionUID = 1L;

		protected Sqlite388(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsWalCheckpointTruncate() {
			return true;
		}
	}

	public static class Sqlite39 extends Sqlite388 {
		private static final long serialVersionUID = 1L;

		protected Sqlite39(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsViewColumnList() {
			return true;
		}

		@Override
		public boolean supportsExpressionIndexes() {
			return true;
		}
	}

	public static class Sqlite315 extends Sqlite39 {
		private static final long serialVersionUID = 1L;

		protected Sqlite315(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsVacuumSchema() {
			return true;
		}

		@Override
		public boolean supportsRowValueComparison() {
			return true;
		}

		@Override
		public boolean supportsRowValueComparisonIn() {
			return true;
		}
	}

	public static class Sqlite318 extends Sqlite315 {
		private static final long serialVersionUID = 1L;

		protected Sqlite318(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsOptimize() {
			return true;
		}
	}

	public static class Sqlite324 extends Sqlite318 {
		private static final long serialVersionUID = 1L;

		protected Sqlite324(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsUpsert() {
			return true;
		}
	}

	public static class Sqlite325 extends Sqlite324 {
		private static final long serialVersionUID = 1L;

		protected Sqlite325(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsRenameColumn() {
			return true;
		}
	}

	public static class Sqlite327 extends Sqlite325 {
		private static final long serialVersionUID = 1L;

		protected Sqlite327(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsVacuumInto() {
			return true;
		}
	}

	public static class Sqlite331 extends Sqlite327 {
		private static final long serialVersionUID = 1L;

		protected Sqlite331(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsGeneratedColumns() {
			return true;
		}

		@Override
		public boolean supportsColumnFormula() {
			return true;
		}
	}

	public static class Sqlite332 extends Sqlite331 {
		private static final long serialVersionUID = 1L;

		protected Sqlite332(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public int getMaxStatementParameterCount() {
			return 32766;
		}
	}

	public static class Sqlite335 extends Sqlite332 {
		private static final long serialVersionUID = 1L;

		protected Sqlite335(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsInsertReturningResultSet() {
			return true;
		}

		@Override
		public boolean supportsDropColumn() {
			return true;
		}
	}

	public static class Sqlite337 extends Sqlite335 {
		private static final long serialVersionUID = 1L;

		protected Sqlite337(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsStrictTables() {
			return true;
		}

		@Override
		public boolean supportsTableList() {
			return true;
		}
	}

	public static class Sqlite346 extends Sqlite337 {
		private static final long serialVersionUID = 1L;

		protected Sqlite346(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsOptimizeAllTables() {
			return true;
		}
	}

	public static class Sqlite353 extends Sqlite346 {
		private static final long serialVersionUID = 1L;

		protected Sqlite353(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsAlterColumnNullability() {
			return true;
		}
	}

	public static class Sqlite3531 extends Sqlite353 {
		private static final long serialVersionUID = 1L;

		protected Sqlite3531(Supplier<Dialect> next) {
			super(next);
		}

		@Override
		public boolean supportsAlterCheckConstraints() {
			return true;
		}
	}
}
