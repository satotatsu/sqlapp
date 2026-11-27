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

/** Rebuilds indexes selected by an Index, Table or explicit Schema model. */
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
		if (object instanceof Schema schema) {
			requireName(schema.getName());
			List<SqlOperation> result = new ArrayList<>();
			for (Table table : schema.getTables()) {
				// Module-owned indexes are maintained through the virtual module, not REINDEX.
				if ("true".equals(table.getSpecifics().get("sqlite.virtual"))
						|| SqliteDdl.isVirtualTable(String.join("\n", table.getDefinition())))
					continue;
				result.addAll(createSql(table));
			}
			return result;
		}
		if (!(object instanceof Table || object instanceof Index))
			throw new IllegalArgumentException("SQLite REBUILD requires a Schema, Table or Index");
		var named = (AbstractSchemaObject<?>) object;
		requireName(named.getName());
		if (named.getSchemaName() == null || named.getSchemaName().isBlank())
			throw new IllegalArgumentException(
					"SQLite REBUILD requires a schema name (main, temp or an attached database)");
		requireName(named.getSchemaName());
		var builder = createSqlBuilder(getDialect());
		builder._add("REINDEX ").name(named.getSchemaName())._add(".").name(named.getName());
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.REBUILD, object);
		return result;
	}

	private static void requireName(String name) {
		if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
			throw new IllegalArgumentException("SQLite REBUILD requires a nonempty object name without NUL");
	}
}
