/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.sqlapp.data.parameter.ParametersContext;

/** Bound, schema-scoped catalog queries shared by SQLite readers. */
final class SqliteMetadataQueries {
	private SqliteMetadataQueries() { }
	static PreparedStatement prepare(Connection connection, ParametersContext context, String projection,
			String joins, String order) throws SQLException {
		return prepare(connection, context, projection, joins, order, false);
	}
	static PreparedStatement prepare(Connection connection, ParametersContext context, String projection,
			String joins, String order, boolean includeViews) throws SQLException {
		Object schemaValue = context.get("schemaName");
		if (schemaValue instanceof Collection<?> values) schemaValue = values.isEmpty() ? null : values.iterator().next();
		else if (schemaValue != null && schemaValue.getClass().isArray()) schemaValue = java.lang.reflect.Array.getLength(schemaValue) == 0 ? null : java.lang.reflect.Array.get(schemaValue, 0);
		String schema = schemaValue == null ? "main" : schemaValue.toString();
		Object filter = context.get("tableName");
		List<String> names = new ArrayList<>();
		if (filter instanceof Collection<?> values) values.forEach(v -> names.add(v.toString()));
		else if (filter != null && filter.getClass().isArray()) {
			for (int i = 0; i < java.lang.reflect.Array.getLength(filter); i++) names.add(java.lang.reflect.Array.get(filter, i).toString());
		} else if (filter != null) names.add(filter.toString());
		String sql = "SELECT m.name AS table_name, m.sql AS table_sql, " + projection + " FROM \""
				+ schema.replace("\"", "\"\"") + "\".sqlite_master m " + joins
				+ (includeViews ? " WHERE m.type IN ('table','view')" : " WHERE m.type='table'") + " AND m.name NOT LIKE 'sqlite\\_%' ESCAPE '\\'"
				+ (names.isEmpty() ? "" : " AND m.name IN (" + String.join(",", java.util.Collections.nCopies(names.size(), "?")) + ")")
				+ " ORDER BY m.name" + order;
		PreparedStatement statement = connection.prepareStatement(sql);
		int parameter = 1;
		int count = (int) (projection + joins).chars().filter(c -> c == '?').count();
		for (int i = 0; i < count; i++) statement.setString(parameter++, schema);
		for (String name : names) statement.setString(parameter++, name);
		return statement;
	}
}
