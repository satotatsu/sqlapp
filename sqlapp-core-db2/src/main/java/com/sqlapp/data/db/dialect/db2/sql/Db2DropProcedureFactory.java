/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Procedure;

public class Db2DropProcedureFactory extends AbstractDropNamedObjectFactory<Procedure, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Procedure routine, Db2SqlBuilder builder) {
		builder.drop().specific().space()._add("PROCEDURE").space();
		Db2RoutineSql.name(builder, routine, true, getOptions().isDecorateSchemaName());
	}
}
