/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;

/** Native objects precede domains, tables and routines that use them. */
public class FirebirdCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	public List<SqlOperation> createSql(Schema schema) {
		return FirebirdSchemaCreateOrder.order(schema, super.createSql(schema));
	}

	@Override
	protected void addCreateSchema(Schema schema, List<SqlOperation> result) {
		// Validate the retained policy, while leaving its application to the explicit
		// restore API.
		com.sqlapp.data.db.dialect.firebird.FirebirdPublications.get(schema);
		for (String definition : FirebirdSchemaObjects.createSql(schema))
			result.add(new SqlOperation(definition, SqlType.CREATE, schema));
	}
}
