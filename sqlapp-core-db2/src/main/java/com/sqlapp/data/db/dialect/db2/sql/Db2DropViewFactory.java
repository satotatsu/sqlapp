/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.View;

public class Db2DropViewFactory extends AbstractDropNamedObjectFactory<View, Db2SqlBuilder> {
	@Override
	protected void addDropObject(View view, Db2SqlBuilder builder) {
		String name = getOptions().isDecorateSchemaName() && view.getSchemaName() != null
				? Db2CreationContext.qualified(view.getSchemaName(), view.getName())
				: Db2CreationContext.identifier(view.getName());
		builder.drop().view().space()._add(name);
	}
}
