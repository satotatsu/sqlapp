/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;

import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Schema;

/** GoogleSQL creates namespaces only for named schemas. */
public class SpannerCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	protected void addCreateSchema(final Schema schema, final List<SqlOperation> operations) {
		final String name = schema.getName();
		if (name == null || name.isEmpty())
			return; // The unnamed default schema is already present in every database.
		if (name.isBlank())
			throw new IllegalArgumentException("Spanner schema name cannot be blank");
		super.addCreateSchema(schema, operations);
	}
}
