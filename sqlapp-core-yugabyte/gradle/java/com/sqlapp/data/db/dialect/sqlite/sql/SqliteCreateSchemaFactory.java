/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;

import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Schema;

/**
 * Creates modeled contents in main, temp or an already attached SQLite
 * database.
 */
public class SqliteCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	protected void addCreateSchema(Schema schema, List<SqlOperation> operations) {
		if (schema.getName() == null || schema.getName().isBlank() || schema.getName().indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"SQLite schema CREATE requires an explicit database name; attach the database before executing its object SQL");
		// SQLite has no CREATE SCHEMA. Filename/opening decisions belong to the
		// caller; the shared factory orders and creates the contained objects.
	}
}
