/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import com.sqlapp.data.db.sql.CreateCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractDbObjectCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/** Catalog creation with domain ordering across Oracle schemas. */
public class Oracle23aiCreateCatalogFactory extends CreateCatalogFactory {

	@Override
	protected void addCreateDbObjectCollection(String property, AbstractDbObjectCollection<?> collection,
			List<SqlOperation> result) {
		if (SchemaObjectProperties.SCHEMAS.getLabel().equals(property) && !collection.isEmpty()) {
			var schemas = collection.stream().map(Schema.class::cast).toList();
			var factory = getSqlFactoryRegistry().getSqlFactory(schemas.get(0), SqlType.CREATE);
			if (schemas.stream().anyMatch(schema -> !schema.getDomains().isEmpty())
					&& factory instanceof Oracle23aiCreateSchemaFactory oracleFactory) {
				result.addAll(oracleFactory.createSchemas(schemas));
				return;
			}
		}
		super.addCreateDbObjectCollection(property, collection, result);
	}
}
