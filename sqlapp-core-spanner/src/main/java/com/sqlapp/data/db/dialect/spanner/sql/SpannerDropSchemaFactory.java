/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Schema;

/** GoogleSQL drops named schemas after their objects have been removed. */
public class SpannerDropSchemaFactory extends AbstractDropNamedObjectFactory<Schema, SpannerSqlBuilder> {
	@Override
	protected void addDropObject(final Schema schema, final SpannerSqlBuilder builder) {
		final String name = schema.getName();
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Spanner DROP SCHEMA requires a named schema");
		if ("INFORMATION_SCHEMA".equalsIgnoreCase(name) || "SPANNER_SYS".equalsIgnoreCase(name))
			throw new IllegalArgumentException("Spanner DROP SCHEMA does not accept a system schema: " + name);
		builder.drop().schema().ifExists(getOptions().isDropIfExists()).name(schema);
	}
}
