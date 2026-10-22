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
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;

/** Rebuilds one SQLite index or all indexes belonging to one table. */
public class SqliteReindexFactory extends AbstractSqlFactory<DbCommonObject<?>, SqliteSqlBuilder> {
	/**
	 * Rebuilds only expression indexes explicitly represented in one Schema model.
	 */
	public List<SqlOperation> createExpressionIndexSql(Schema schema) {
		java.util.Objects.requireNonNull(schema, "schema");
		if (schema.getName() == null || schema.getName().isBlank())
			throw new IllegalArgumentException("SQLite expression-index rebuild requires a named Schema");
		if (!((Sqlite) getDialect()).supportsExpressionIndexes())
			throw new IllegalArgumentException("SQLite expression indexes require SQLite 3.9 or later");
		List<SqlOperation> result = new ArrayList<>();
		for (Table table : schema.getTables()) {
			for (Index index : table.getIndexes()) {
				String keys = index.getSpecifics().get("sqlite.index.keys");
				if (keys == null || keys.isBlank() || !SqliteDdl.hasIndexExpressions(keys))
					continue;
				if (!schema.getName().equalsIgnoreCase(index.getSchemaName()))
					throw new IllegalArgumentException(
							"SQLite expression index is outside the selected schema: " + index.getName());
				result.addAll(createSql(index));
			}
		}
		return result;
	}

	@Override
	public List<SqlOperation> createSql(DbCommonObject<?> object) {
		if (!(object instanceof Table || object instanceof Index))
			throw new IllegalArgumentException(
					"SQLite REBUILD requires a Table or Index; schema-wide rebuilding is not supported");
		var named = (AbstractSchemaObject<?>) object;
		if (named.getName() == null || named.getName().isBlank())
			throw new IllegalArgumentException("SQLite REBUILD requires a nonempty object name");
		if (named.getSchemaName() == null || named.getSchemaName().isBlank())
			throw new IllegalArgumentException(
					"SQLite REBUILD requires a schema name (main, temp or an attached database)");
		var builder = createSqlBuilder(getDialect());
		builder._add("REINDEX ").name(named.getSchemaName())._add(".").name(named.getName());
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.REBUILD, object);
		return result;
	}
}
