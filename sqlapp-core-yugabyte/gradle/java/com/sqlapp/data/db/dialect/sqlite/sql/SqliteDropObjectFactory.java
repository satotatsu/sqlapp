/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.Index;

/**
 * SQLite schema-scoped DROP and explicit rejection of standalone constraint
 * removal.
 */
public class SqliteDropObjectFactory extends AbstractDropNamedObjectFactory<AbstractSchemaObject<?>, SqliteSqlBuilder> {
	@Override
	protected void addDropObject(AbstractSchemaObject<?> object, SqliteSqlBuilder builder) {
		if (object instanceof Constraint)
			throw new IllegalArgumentException("SQLite constraint removal requires a separately planned table rebuild");
		if (object instanceof Index
				&& object.getName().toLowerCase(java.util.Locale.ROOT).startsWith("sqlite_autoindex_"))
			throw new IllegalArgumentException("SQLite internal indexes cannot be dropped directly");
		builder.drop().space()._add(object.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT)).space()
				.name(object, object.getSchemaName() != null);
	}
}
