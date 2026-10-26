/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
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
		var foreignKeys = schema.getTables().stream()
				.flatMap(table -> table.getConstraints().getForeignKeyConstraints().stream()).toList();
		if (foreignKeys.isEmpty())
			return FirebirdSchemaCreateOrder.order(schema, super.createSql(schema));
		// Generate and validate through the ordinary table/constraint factories before
		// separating the foreign keys. This also preserves external-table validation.
		for (var table : schema.getTables())
			if (!table.getConstraints().getForeignKeyConstraints().isEmpty())
				getSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		var constraints = FirebirdSchemaForeignKeys.create(getSqlFactoryRegistry(), foreignKeys);
		var definitions = FirebirdSchemaForeignKeys.without(schema, foreignKeys);
		var result = new ArrayList<>(FirebirdSchemaCreateOrder.order(definitions, super.createSql(definitions)));
		result.addAll(constraints);
		return result;
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
