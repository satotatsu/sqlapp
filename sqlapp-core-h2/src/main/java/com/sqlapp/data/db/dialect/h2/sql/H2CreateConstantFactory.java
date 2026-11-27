/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.schemas.Constant;

/** Constants retain their complete executable CREATE definition. */
public class H2CreateConstantFactory extends AbstractCreateNamedObjectFactory<Constant, H2SqlBuilder> {
	@Override
	protected void addCreateObject(Constant object, H2SqlBuilder builder) {
		if (object.getDefinition().isEmpty())
			throw new IllegalArgumentException("H2 constant requires a complete CREATE CONSTANT definition");
		builder._add(object.getDefinition());
	}
}
