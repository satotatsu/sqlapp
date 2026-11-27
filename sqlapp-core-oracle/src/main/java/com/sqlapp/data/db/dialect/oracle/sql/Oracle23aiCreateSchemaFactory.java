/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/**
 * Creates catalog schemas by object kind so cross-schema domains precede their
 * users.
 */
public class Oracle23aiCreateSchemaFactory extends OracleCreateSchemaFactory {

	List<SqlOperation> createSchemas(List<Schema> schemas) {
		List<SqlOperation> result = new ArrayList<>();
		for (Schema schema : schemas)
			addCreateSchema(schema, result);
		for (String property : getCreateCollectionOrder()) {
			if (SchemaObjectProperties.DOMAINS.getLabel().equals(property)) {
				List<Domain> domains = new ArrayList<>();
				for (Schema schema : schemas)
					domains.addAll(schema.getDomains());
				addCreateSortedSchemaObjectCollection(property, OracleDomainDependencies.sort(domains), result);
			} else {
				for (Schema schema : schemas) {
					var collection = (AbstractSchemaObjectCollection<?>) schema.toMap().get(property);
					if (collection != null)
						addCreateSchemaObjectCollection(property, collection, result);
				}
			}
		}
		return result;
	}
}
