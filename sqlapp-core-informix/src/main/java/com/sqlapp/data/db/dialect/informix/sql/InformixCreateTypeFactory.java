/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.util.AbstractSqlBuilder;

/** Named ROW definitions are complete native DDL; opaque types need their native libraries. */
public class InformixCreateTypeFactory extends AbstractCreateNamedObjectFactory<Type, AbstractSqlBuilder<?>> {
	@Override protected void addCreateObject(final Type type, final AbstractSqlBuilder<?> builder) {
		if (type.getDefinition().isEmpty() || type.getDefinition().stream().allMatch(String::isBlank))
			throw new IllegalArgumentException("Complete Informix CREATE TYPE definition is required: " + type.getName());
		builder._add(type.getDefinition());
	}
}
