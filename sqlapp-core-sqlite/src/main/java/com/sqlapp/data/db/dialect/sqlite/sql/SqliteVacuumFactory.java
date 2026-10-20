/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;

/**
 * Database-scoped VACUUM; never widens a table request to the entire database.
 */
public class SqliteVacuumFactory extends AbstractSqlFactory<DbCommonObject<?>, SqliteSqlBuilder> {
	protected SqlType getSqlType() {
		return SqlType.DEFRAG;
	}

	@Override
	public List<SqlOperation> createSql(DbCommonObject<?> object) {
		if (!(object instanceof Schema schema) || schema.getName() == null || schema.getName().isBlank())
			throw new IllegalArgumentException(
					"SQLite VACUUM operates on a database: supply a named Schema, not a Table or Index");
		var builder = createSqlBuilder(getDialect());
		builder._add("VACUUM");
		if (((Sqlite) getDialect()).supportsVacuumSchema()) {
			builder.space().name(schema.getName());
		} else if (!"main".equalsIgnoreCase(schema.getName())) {
			throw new IllegalArgumentException("VACUUM of an attached database requires SQLite 3.15 or later");
		}
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, getSqlType(), object);
		return result;
	}

	public static class Full extends SqliteVacuumFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.DEFRAG_FULL;
		}
	}
}
