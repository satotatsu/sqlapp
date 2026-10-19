/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Inline details remain usable by CREATE TABLE; standalone ADD is unsupported.
 */
public final class SqliteConstraintFactories {
	private SqliteConstraintFactories() {
	}

	public static class CreateCheck extends AbstractCreateCheckConstraintFactory<SqliteSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(CheckConstraint constraint) {
			throw new IllegalArgumentException("SQLite CHECK addition requires a separately planned table rebuild");
		}
	}

	public static class CreateUnique extends AbstractCreateUniqueConstraintFactory<SqliteSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(UniqueConstraint constraint) {
			throw new IllegalArgumentException(
					"SQLite PRIMARY KEY/UNIQUE addition requires a separately planned table rebuild");
		}
	}
}
