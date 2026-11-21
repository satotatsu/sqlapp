/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.sqlapp.data.parameter.ParametersContext;

/** Bound, schema-scoped catalog queries shared by SQLite readers. */
final class SqliteMetadataQueries {
	/**
	 * Table names bound per query. SQLite before 3.32 accepts at most 999
	 * parameters, so larger name sets are read in a few set-based queries.
	 */
	static final int MAX_NAMES_PER_QUERY = 500;

	/** Receives each catalog row. */
	@FunctionalInterface
	interface RowHandler {
		void accept(ResultSet resultSet) throws SQLException;
	}

	private SqliteMetadataQueries() {
	}

	static void forEach(Connection connection, ParametersContext context, String projection, String joins, String order,
			RowHandler handler) throws SQLException {
		forEach(connection, context, projection, joins, order, false, handler);
	}

	/**
	 * Runs the catalog query for the context's schema and table names, in name
	 * chunks that preserve the requested order.
	 */
	static void forEach(Connection connection, ParametersContext context, String projection, String joins, String order,
			boolean includeViews, RowHandler handler) throws SQLException {
		String schema = schemaName(context);
		for (List<String> names : chunks(tableNames(context))) {
			try (var statement = prepare(connection, schema, names, projection, joins, order, includeViews);
					var resultSet = statement.executeQuery()) {
				while (resultSet.next())
					handler.accept(resultSet);
			}
		}
	}

	/** Splits names for binding; an empty list is one unfiltered query. */
	static List<List<String>> chunks(List<String> names) {
		if (names.size() <= MAX_NAMES_PER_QUERY)
			return List.of(names);
		List<List<String>> result = new ArrayList<>();
		for (int i = 0; i < names.size(); i += MAX_NAMES_PER_QUERY)
			result.add(names.subList(i, Math.min(names.size(), i + MAX_NAMES_PER_QUERY)));
		return result;
	}

	/** Table names from a single value, a collection or an array. */
	static List<String> tableNames(ParametersContext context) {
		Object filter = context.get("tableName");
		List<String> names = new ArrayList<>();
		if (filter instanceof Collection<?> values)
			values.forEach(v -> names.add(v.toString()));
		else if (filter != null && filter.getClass().isArray()) {
			for (int i = 0; i < java.lang.reflect.Array.getLength(filter); i++)
				names.add(java.lang.reflect.Array.get(filter, i).toString());
		} else if (filter != null)
			names.add(filter.toString());
		return names;
	}

	private static String schemaName(ParametersContext context) {
		Object schemaValue = context.get("schemaName");
		if (schemaValue instanceof Collection<?> values)
			schemaValue = values.isEmpty() ? null : values.iterator().next();
		else if (schemaValue != null && schemaValue.getClass().isArray())
			schemaValue = java.lang.reflect.Array.getLength(schemaValue) == 0 ? null
					: java.lang.reflect.Array.get(schemaValue, 0);
		return schemaValue == null ? "main" : schemaValue.toString();
	}

	private static PreparedStatement prepare(Connection connection, String schema, List<String> names,
			String projection, String joins, String order, boolean includeViews) throws SQLException {
		String sql = "SELECT m.name AS table_name, m.sql AS table_sql, " + projection + " FROM \""
				+ schema.replace("\"", "\"\"") + "\".sqlite_master m " + joins
				+ (includeViews ? " WHERE m.type IN ('table','view')" : " WHERE m.type='table'")
				+ " AND m.name NOT LIKE 'sqlite\\_%' ESCAPE '\\'"
				+ (names.isEmpty() ? ""
						: " AND m.name IN (" + String.join(",", java.util.Collections.nCopies(names.size(), "?")) + ")")
				+ " ORDER BY m.name" + order;
		PreparedStatement statement = connection.prepareStatement(sql);
		try {
			int parameter = 1;
			int count = (int) (projection + joins).chars().filter(c -> c == '?').count();
			for (int i = 0; i < count; i++)
				statement.setString(parameter++, schema);
			for (String name : names)
				statement.setString(parameter++, name);
			return statement;
		} catch (SQLException | RuntimeException e) {
			statement.close();
			throw e;
		}
	}
}
