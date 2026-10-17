/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;

final class OracleSynonymTarget {
	private OracleSynonymTarget() {
	}

	static boolean validateChange(com.sqlapp.data.schemas.DbObjectDifference difference, com.sqlapp.data.db.dialect.Dialect dialect) {
		var properties = difference.toDifference().getChangedProperties(dialect);
		for (String property : properties.keySet())
			if (!java.util.Set.of(com.sqlapp.data.schemas.SchemaProperties.OBJECT_SCHEMA_NAME.getLabel(),
					com.sqlapp.data.schemas.SchemaProperties.OBJECT_NAME.getLabel(),
					com.sqlapp.data.schemas.SchemaProperties.DB_LINK_NAME.getLabel()).contains(property))
				throw new IllegalArgumentException("Synonym replacement supports only target changes: " + property);
		return !properties.isEmpty();
	}
	static void add(String schema, String object, String link, OracleSqlBuilder builder) {
		if (object == null || object.isBlank())
			throw new IllegalArgumentException("Synonym target object is required");
		if (link != null && !link.matches("[A-Za-z][A-Za-z0-9_$#]*(?:\\.[A-Za-z0-9_$#]+)*"))
			throw new IllegalArgumentException("Unsupported Oracle database link name: " + link);
		builder.space().names(schema, object);
		if (link != null)
			builder._add("@")._add(link);
	}
}
