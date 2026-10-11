/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres110CreateIndexFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Index;

public class CockroachCreateIndexFactory extends Postgres110CreateIndexFactory {
	@Override
	protected void addIncludes(Index index, com.sqlapp.data.schemas.Table table, PostgresSqlBuilder builder) {
		if (!index.getIncludes().isEmpty())
			builder._add(" STORING (").names(index.getIncludes())._add(")");
	}

	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Index index) {
		var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
		if (!createIndex(index))
			return result;
		var builder = createSqlBuilder();
		addCreateObject(index, builder);
		addSql(result, builder, com.sqlapp.data.db.sql.SqlType.CREATE, index);
		if (index.getRemarks() != null) {
			builder = createSqlBuilder().comment().on().index().space();
			CockroachDropIndexFactory.name(index, builder, getOptions().isDecorateSchemaName());
			builder.is().sqlChar(index.getRemarks());
			addSql(result, builder, com.sqlapp.data.db.sql.SqlType.SET_COMMENT, index);
		}
		return result;
	}

	@Override
	public void addCreateObject(Index index, PostgresSqlBuilder builder) {
		if (index.getDefinition() != null && !index.getDefinition().isEmpty())
			builder._add(index.getDefinition());
		else
			super.addCreateObject(index, builder);
	}
}
