/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject.Kind;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;

/**
 * Adds PostgreSQL-only definitions at their schema creation dependency
 * boundaries.
 */
public class PostgresCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	protected void addCreateSortedSchemaObjectCollection(String property,
			List<? extends AbstractSchemaObject<?>> objects, List<SqlOperation> sql) {
		super.addCreateSortedSchemaObjectCollection(property,
				objects.stream().filter(o -> !o.getSpecifics().containsKey("postgres.extension")).toList(), sql);
	}

	@Override
	protected void addCreateSchema(Schema schema, List<SqlOperation> sql) {
		super.addCreateSchema(schema, sql);
		append(schema, Kind.COLLATION, sql);
	}

	@Override
	protected void addCreateSchemaObjectCollection(String property, AbstractSchemaObjectCollection<?> collection,
			List<SqlOperation> sql) {
		if (property.equals("views")) {
			append(collection.getSchema(), Kind.FOREIGN_TABLE, sql);
		}
		super.addCreateSchemaObjectCollection(property, collection, sql);
	}

	@Override
	public List<SqlOperation> createSql(Schema schema) {
		var sql = super.createSql(schema);
		append(schema, Kind.POLICY, sql);
		append(schema, Kind.ROW_SECURITY, sql);
		append(schema, Kind.STATISTICS, sql);
		return sql;
	}

	private void append(Schema schema, Kind kind, List<SqlOperation> sql) {
		var selected = PostgresSchemaObjects.get(schema).stream().filter(o -> o.kind() == kind).toList();
		for (var object : selected) {
			for (String definition : PostgresSchemaObjects.createSql(object, Map.of())) {
				sql.add(new SqlOperation(definition, SqlType.CREATE, schema));
			}
		}
	}
}
