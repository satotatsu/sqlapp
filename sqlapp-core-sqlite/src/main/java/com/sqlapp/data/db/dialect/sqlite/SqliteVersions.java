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

	public static class Sqlite380 extends Sqlite {
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

	public static class Sqlite39 extends Sqlite383 {
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
		public boolean supportsRowValueComparison() {
			return true;
		}

		@Override
		public boolean supportsRowValueComparisonIn() {
			return true;
		}
	}

	public static class Sqlite324 extends Sqlite315 {
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

	public static class Sqlite331 extends Sqlite325 {
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
	}
}
