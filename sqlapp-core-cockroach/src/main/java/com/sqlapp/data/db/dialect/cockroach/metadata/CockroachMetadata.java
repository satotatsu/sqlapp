/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.parameter.ParametersContext;

final class CockroachMetadata {
	private CockroachMetadata() {
	}

	static boolean userSchema(String name) {
		return !name.startsWith("pg_") && !Set.of("information_schema", "crdb_internal").contains(name);
	}

	static boolean matches(ParametersContext context, String key, String value) {
		Object filter = context.get(key);
		if (filter == null)
			return true;
		if (filter instanceof Collection<?> values)
			return values.isEmpty() || values.contains(value);
		if (filter instanceof Object[] values)
			return values.length == 0 || Arrays.asList(values).contains(value);
		return filter.toString().isEmpty() || filter.equals(value);
	}

	static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	static String fullName(String schema, String name) {
		return quote(schema) + "." + quote(name);
	}

	static String definition(Connection c, String kind, String schema, String name) throws SQLException {
		try (var sql = c.createStatement();
				var rows = sql.executeQuery("SHOW CREATE " + kind + " " + fullName(schema, name))) {
			if (!rows.next())
				throw new SQLException("No CockroachDB CREATE definition: " + name);
			return rows.getString("create_statement");
		}
	}
}
