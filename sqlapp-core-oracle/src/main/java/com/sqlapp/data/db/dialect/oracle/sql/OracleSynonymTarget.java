/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;

final class OracleSynonymTarget {
	private OracleSynonymTarget() {
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
