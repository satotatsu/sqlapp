/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Function;

public class Db2DropFunctionFactory extends AbstractDropNamedObjectFactory<Function, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Function routine, Db2SqlBuilder builder) {
		builder.drop().specific().space()._add("FUNCTION").space();
		if (getOptions().isDecorateSchemaName() && routine.getSchemaName() != null)
			builder.name(routine.getSchemaName())._add(".");
		builder.name(routine.getSpecificName());
	}
}
