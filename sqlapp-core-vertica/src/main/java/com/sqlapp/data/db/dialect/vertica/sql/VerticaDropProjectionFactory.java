/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Projection;

/** Drops the named catalog object, without expanding to its buddy base name. */
public class VerticaDropProjectionFactory extends AbstractDropNamedObjectFactory<Projection, VerticaSqlBuilder> {
	@Override
	protected void addDropObject(Projection projection, VerticaSqlBuilder builder) {
		if (projection.getName() == null || projection.getName().isBlank())
			throw new IllegalArgumentException("Vertica projection DROP requires a name");
		builder.drop().space()._add("PROJECTION").ifExists(getOptions().isDropIfExists()).space().name(projection,
				getOptions().isDecorateSchemaName());
	}
}
