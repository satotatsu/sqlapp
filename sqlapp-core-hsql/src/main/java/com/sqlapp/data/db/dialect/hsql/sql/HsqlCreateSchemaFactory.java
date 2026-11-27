/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.hsql.HsqlDdlDependencies;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Schema;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources.Source;

public class HsqlCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	public List<SqlOperation> createSql(Schema schema) {
		return createSql(schema, Map.of());
	}

	public List<SqlOperation> createSql(Schema schema, Map<String, Source> bindings) {
		return HsqlDdlDependencies.order(collectSql(schema, bindings), List.of(schema), getSqlFactoryRegistry());
	}

	List<SqlOperation> collectSql(Schema schema, Map<String, Source> bindings) {
		HsqlTextTableSources.validateBindings(bindings, schema.getTables().stream().filter(HsqlTextTableSources::isText)
				.map(HsqlTextTableSources::id).collect(Collectors.toSet()));
		var operations = new ArrayList<SqlOperation>();
		addCreateSchema(schema, operations);
		var values = schema.toMap();
		for (String property : getCreateCollectionOrder()) {
			if ("tables".equals(property)) {
				for (var table : schema.getTables()) {
					// Defer references so both sides of a foreign-key cycle exist first.
					var definition = table.clone();
					for (var fk : new ArrayList<>(definition.getConstraints().getForeignKeyConstraints())) {
						definition.getConstraints().remove(fk);
					}
					com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Table> factory = getSqlFactoryRegistry()
							.getSqlFactory(table, SqlType.CREATE);
					String id = HsqlTextTableSources.id(table);
					if (factory instanceof HsqlCreateTableFactory hsql) {
						operations.addAll(hsql.createSql(definition,
								bindings.containsKey(id) ? Map.of(id, bindings.get(id)) : Map.of()));
					} else {
						if (bindings.containsKey(id))
							throw new IllegalArgumentException(
									"Custom table factory must support explicit HSQLDB text sources");
						operations.addAll(factory.createSql(definition));
					}
					for (var fk : table.getConstraints().getForeignKeyConstraints()) {
						operations.addAll(getSqlFactoryRegistry().createSql(fk, SqlType.CREATE));
					}
				}
			} else if (values.get(property) instanceof AbstractSchemaObjectCollection<?> collection) {
				addCreateSchemaObjectCollection(property, collection, operations);
			}
		}
		return operations;
	}
}
