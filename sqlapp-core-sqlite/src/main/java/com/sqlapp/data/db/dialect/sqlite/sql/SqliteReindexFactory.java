/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/** Rebuilds one SQLite index or all indexes belonging to one table. */
public class SqliteReindexFactory extends AbstractSqlFactory<DbCommonObject<?>, SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(DbCommonObject<?> object) {
		if (!(object instanceof Table || object instanceof Index))
			throw new IllegalArgumentException("SQLite REBUILD requires a Table or Index; schema-wide rebuilding is not supported");
		var named = (AbstractSchemaObject<?>) object;
		if (named.getName() == null || named.getName().isBlank())
			throw new IllegalArgumentException("SQLite REBUILD requires a nonempty object name");
		if (named.getSchemaName() == null || named.getSchemaName().isBlank())
			throw new IllegalArgumentException("SQLite REBUILD requires a schema name (main, temp or an attached database)");
		var builder = createSqlBuilder(getDialect());
		builder._add("REINDEX ").name(named.getSchemaName())._add(".").name(named.getName());
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.REBUILD, object);
		return result;
	}
}
