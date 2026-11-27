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
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/** SQLite ANALYZE for one explicitly identified schema, table or index. */
public class SqliteAnalyzeFactory extends AbstractSqlFactory<DbCommonObject<?>, SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(DbCommonObject<?> object) {
		var builder = createSqlBuilder(getDialect());
		builder.analyze().space();
		if (object instanceof Schema schema) {
			requireName(schema.getName());
			builder.name(schema.getName());
		} else if (object instanceof Table || object instanceof Index) {
			var named = (AbstractSchemaObject<?>) object;
			requireName(named.getName());
			String schema = named.getSchemaName();
			if (schema == null || schema.isBlank())
				throw new IllegalArgumentException(
						"SQLite ANALYZE requires a schema name (main, temp or an attached database)");
			builder.name(schema)._add(".").name(named.getName());
		} else {
			throw new IllegalArgumentException("SQLite ANALYZE requires a Schema, Table or Index");
		}
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.ANALYZE, object);
		return result;
	}

	private void requireName(String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("SQLite ANALYZE requires a nonempty object name");
	}
}
