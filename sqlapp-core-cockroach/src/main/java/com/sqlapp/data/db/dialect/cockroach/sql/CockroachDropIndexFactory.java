/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Index;

/** Cockroach index names are scoped to their owning table. */
public class CockroachDropIndexFactory extends AbstractDropNamedObjectFactory<Index, PostgresSqlBuilder> {
	@Override protected void addDropObject(Index index, PostgresSqlBuilder builder) {
		builder.drop().index().space();
		name(index,builder,getOptions().isDecorateSchemaName());
	}
	static void name(Index index,PostgresSqlBuilder builder,boolean schema) {
		if(index.getTableName()==null) throw new IllegalArgumentException("CockroachDB index operation requires its owning table");
		if(schema && index.getSchemaName()!=null) builder._add(builder.getDialect().quote(index.getSchemaName()))._add(".");
		builder._add(builder.getDialect().quote(index.getTableName()))._add("@")._add(builder.getDialect().quote(index.getName()));
	}
}
