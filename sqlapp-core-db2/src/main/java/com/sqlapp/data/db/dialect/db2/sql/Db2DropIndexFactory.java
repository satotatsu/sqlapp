/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Index;

/** The catalog index schema can differ from its table's schema. */
public class Db2DropIndexFactory extends AbstractDropNamedObjectFactory<Index, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Index index, Db2SqlBuilder builder) {
		String schema = index.getSpecifics().getOrDefault("INDEX_SCHEMA", index.getSchemaName());
		builder._add("DROP INDEX " + (schema == null ? Db2CreationContext.identifier(index.getName())
				: Db2CreationContext.qualified(schema, index.getName())));
	}
}
