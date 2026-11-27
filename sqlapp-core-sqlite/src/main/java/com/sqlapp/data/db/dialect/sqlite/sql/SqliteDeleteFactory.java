/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.DeleteByRootRowsFactory;
import com.sqlapp.data.db.sql.DeleteAllTableFactory;
import com.sqlapp.data.db.sql.DeleteForAppFactory;
import com.sqlapp.data.db.sql.DeleteFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** SQLite key-based DELETE retaining shared optimistic-lock conditions. */
public class SqliteDeleteFactory extends DeleteFactory {
	@Override
	protected AbstractSqlBuilder<?> newSqlBuilder(Dialect dialect) {
		return new SqliteDmlSqlBuilder(dialect);
	}

	@Override
	protected void addDeleteConditionColumns(Table table, SqlSignature signature, AbstractSqlBuilder<?> builder) {
		SqliteKeyColumns.addWhere(table, signature, builder, this::getValueDefinitionSimple,
				this::addWhereColumnComment);
		addLockVersionColumnCondition(table, builder);
	}

	public static class ByRootRows extends DeleteByRootRowsFactory {
		@Override
		protected AbstractSqlBuilder<?> newSqlBuilder(Dialect dialect) {
			return new SqliteDmlSqlBuilder(dialect);
		}
	}

	public static class All extends DeleteAllTableFactory {
		@Override
		protected AbstractSqlBuilder<?> newSqlBuilder(Dialect dialect) {
			return new SqliteDmlSqlBuilder(dialect);
		}
	}

	public static class ForApp extends DeleteForAppFactory {
		@Override
		protected AbstractSqlBuilder<?> newSqlBuilder(Dialect dialect) {
			return new SqliteDmlSqlBuilder(dialect);
		}
	}
}
