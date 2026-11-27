/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTypeFactory;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.util.CommonUtils;

/** Creates a distinct type from its source representation or complete DDL. */
public class HsqlCreateTypeFactory extends AbstractCreateTypeFactory<HsqlSqlBuilder> {
	@Override
	protected void addCreateObject(Type obj, HsqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(obj.getStatement()) || !obj.getColumns().isEmpty()) {
			throw new IllegalArgumentException(
					"HSQLDB distinct type requires a scalar source statement: " + obj.getName());
		}
		builder.create().type().name(obj, getOptions().isDecorateSchemaName()).as().space()._add(obj.getStatement());
	}
}
