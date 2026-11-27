/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Function;

/**
 * H2 Java functions are dropped as aliases, without a SQL argument signature.
 */
public class H2DropFunctionFactory extends AbstractDropNamedObjectFactory<Function, H2SqlBuilder> {
	@Override
	protected void addDropObject(Function object, H2SqlBuilder builder) {
		builder.drop().alias().ifExists(getOptions().isDropIfExists()).name(object,
				getOptions().isDecorateSchemaName());
	}
}
