/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.View;

public class PhoenixDropViewFactory extends AbstractDropNamedObjectFactory<View, PhoenixSqlBuilder> {
	@Override
	protected void addDropObject(View view, PhoenixSqlBuilder builder) {
		builder.drop().view().ifExists(getOptions().isDropIfExists()).name(view, getOptions().isDecorateSchemaName());
	}
}
