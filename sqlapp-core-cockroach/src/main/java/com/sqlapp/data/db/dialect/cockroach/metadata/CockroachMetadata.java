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

	static Map<List<String>, String> definitions(Connection c, String kind, ParametersContext context) throws SQLException {
		var result = new LinkedHashMap<List<String>, String>();
		var parameters = new ArrayList<String>();
		parameters.add(kind.toLowerCase(java.util.Locale.ROOT));
		var query = new StringBuilder("SELECT schema_name,descriptor_name,create_statement FROM crdb_internal.create_statements WHERE database_name=current_database() AND descriptor_type=?");
		addFilter(query, parameters, "schema_name", context.get("schemaName"));
		addFilter(query, parameters, "descriptor_name", context.get(kind.toLowerCase(java.util.Locale.ROOT) + "Name"));
		try (var sql = c.prepareStatement(query.toString())) {
			for (int i=0;i<parameters.size();i++) sql.setString(i+1, parameters.get(i));
			try (var rows = sql.executeQuery()) {
				while (rows.next()) {
					var key = List.of(rows.getString(1), rows.getString(2));
					if (result.putIfAbsent(key, rows.getString(3)) != null)
						throw new SQLException("Ambiguous CockroachDB CREATE definition: " + key);
				}
			}
		}
		return result;
	}

	private static void addFilter(StringBuilder query, List<String> parameters, String column, Object filter) {
		if (filter == null) return;
		Collection<?> values = filter instanceof Collection<?> collection ? collection
				: filter instanceof Object[] array ? Arrays.asList(array) : List.of(filter);
		if (values.isEmpty() || values.size() == 1 && "".equals(values.iterator().next())) return;
		query.append(" AND ").append(column).append(" IN (")
				.append(String.join(",", Collections.nCopies(values.size(), "?"))).append(")");
		for (var value : values) parameters.add(value.toString());
	}

	static String definition(Map<List<String>, String> definitions, String schema, String name) throws SQLException {
		String result = definitions.get(List.of(schema, name));
		if (result == null || result.isBlank()) throw new SQLException("No CockroachDB CREATE definition: " + fullName(schema, name));
		return result;
	}
}
