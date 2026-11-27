/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.hsql.HsqlDdlDependencies;
import com.sqlapp.data.db.sql.CreateCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Catalog;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import com.sqlapp.data.schemas.AbstractDbObjectCollection;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources.Source;
import com.sqlapp.data.db.sql.SqlType;

public class HsqlCreateCatalogFactory extends CreateCatalogFactory {
	@Override
	protected java.util.Set<String> getCreateCollectionOrder() {
		var order = new java.util.LinkedHashSet<>(super.getCreateCollectionOrder());
		order.add(com.sqlapp.data.schemas.SchemaObjectProperties.ROUTINE_PRIVILEGES.getLabel());
		return order;
	}

	@Override
	public List<SqlOperation> createSql(Catalog catalog) {
		return createSql(catalog, Map.of());
	}

	public List<SqlOperation> createSql(Catalog catalog, Map<String, Source> bindings) {
		HsqlTextTableSources.validateBindings(bindings,
				catalog.getSchemas().stream().flatMap(s -> s.getTables().stream()).filter(HsqlTextTableSources::isText)
						.map(HsqlTextTableSources::id).collect(Collectors.toSet()));
		var operations = new ArrayList<SqlOperation>();
		var values = catalog.toMap();
		for (String property : getCreateCollectionOrder()) {
			if ("schemas".equals(property)) {
				for (var schema : catalog.getSchemas()) {
					var selected = new LinkedHashMap<String, Source>();
					for (var table : schema.getTables()) {
						String id = HsqlTextTableSources.id(table);
						if (bindings.containsKey(id))
							selected.put(id, bindings.get(id));
					}
					com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Schema> factory = getSqlFactoryRegistry()
							.getSqlFactory(schema, SqlType.CREATE);
					if (factory.getClass() == HsqlCreateSchemaFactory.class
							&& factory instanceof HsqlCreateSchemaFactory hsql) {
						operations.addAll(hsql.collectSql(schema, selected));
					} else if (factory instanceof HsqlCreateSchemaFactory hsql) {
						operations.addAll(hsql.createSql(schema, selected));
					} else {
						if (!selected.isEmpty())
							throw new IllegalArgumentException(
									"Custom schema factory must support explicit HSQLDB text sources");
						operations.addAll(factory.createSql(schema));
					}
				}
			} else if (values.get(property) instanceof AbstractDbObjectCollection<?> collection) {
				addCreateDbObjectCollection(property, collection, operations);
			}
		}
		return HsqlDdlDependencies.order(operations, catalog.getSchemas(), getSqlFactoryRegistry());
	}
}
