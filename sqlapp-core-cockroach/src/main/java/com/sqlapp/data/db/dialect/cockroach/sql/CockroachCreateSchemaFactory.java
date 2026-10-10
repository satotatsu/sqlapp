/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSchemaFactory;
import com.sqlapp.data.schemas.Schema;

/**
 * Public is created with the database; honor the shared CREATE-if-absent
 * option.
 */
public class CockroachCreateSchemaFactory extends AbstractCreateSchemaFactory<PostgresSqlBuilder> {
	@Override
	protected void addCreateObject(Schema schema, PostgresSqlBuilder builder) {
		builder.create().schema();
		if (getOptions().isCreateIfNotExists())
			builder._add(" IF NOT EXISTS");
		builder.space().name(schema);
	}
}
