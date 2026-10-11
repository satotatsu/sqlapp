/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;

/** Uses table-valued PRAGMAs on SQLite 3.16+, retaining the legacy JDBC path. */
public class SqliteIndexReader extends JdbcIndexReader {

	public SqliteIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Index> result = list();
		try {
			if (supportsBulkIndexes(productVersionInfo)) {
				return readBulkIndexes(connection, context);
			}
			for (String tableName : tableNames(context)) {
				final ParametersContext tableContext = context.clone();
				tableContext.put(SchemaProperties.TABLE_NAME.getLabel(), tableName);
				result.addAll(getAllIndex(connection.getMetaData(), tableContext, false));
			}
			loadPartialIndexPredicates(connection, result);
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	static boolean supportsBulkIndexes(final ProductVersionInfo version) {
		return version.getMajorVersion() != null && (version.getMajorVersion() > 3
				|| version.getMajorVersion() == 3 && version.getMinorVersion() != null
						&& version.getMinorVersion() >= 16);
	}

	private List<Index> readBulkIndexes(final Connection connection, final ParametersContext context)
			throws SQLException {
		final Map<String, Index> indexes = new LinkedHashMap<>();
		final List<String> tables = tableNames(context);
		final String requestedIndex = getObjectName(context);
		final String schema = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final String sql = "SELECT m.name AS table_name, il.name AS index_name, il.\"unique\" AS is_unique, "
				+ "d.sql AS definition, x.* FROM " + quoteIdentifier(schema) + ".sqlite_master m "
				+ "JOIN pragma_index_list(m.name, ?) il JOIN pragma_index_xinfo(il.name, ?) x "
				+ "LEFT JOIN " + quoteIdentifier(schema) + ".sqlite_master d ON d.type='index' AND d.name=il.name "
				+ "WHERE m.type='table' AND x.key=1"
				+ (tables.isEmpty() ? "" : " AND m.name IN (" + String.join(",", java.util.Collections.nCopies(tables.size(), "?")) + ")")
				+ (requestedIndex == null ? "" : " AND il.name=?")
				+ " ORDER BY m.name, il.name, x.seqno";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, schema);
			statement.setString(2, schema);
			for (int i = 0; i < tables.size(); i++) {
				statement.setString(i + 3, tables.get(i));
			}
			if (requestedIndex != null) statement.setString(tables.size() + 3, requestedIndex);
			try (var rs = statement.executeQuery()) {
				while (rs.next()) {
					final String name = rs.getString("index_name");
					Index index = indexes.get(name);
					if (index == null) {
						index = new Index(name);
						index.setDialect(getDialect());
						index.setCatalogName(getCatalogName(context));
						index.setSchemaName(getSchemaName(context));
						index.setTableName(rs.getString("table_name"));
						index.setUnique(rs.getBoolean("is_unique"));
						index.setWhere(extractWhere(rs.getString("definition")));
						retainKeys(index, rs.getString("definition"));
						indexes.put(name, index);
					}
					final Order order = rs.getBoolean("desc") ? Order.Desc : Order.Asc;
					final String column = rs.getString("name");
					if (column != null) {
						index.getColumns().add(new Column(column), order);
					} else if (rs.getInt("cid") == -2) {
						final List<String> keys = SqliteColumnReader.splitColumnDefinitions(rs.getString("definition"));
						index.getColumns().add(stripOrder(keys.get(rs.getInt("seqno"))), order);
					}
				}
			}
		}
		return new java.util.ArrayList<>(indexes.values());
	}

	private void loadPartialIndexPredicates(final Connection connection, final List<Index> indexes)
			throws SQLException {
		for (Index index : indexes) {
			final String schemaName = index.getSchemaName() == null ? "main" : index.getSchemaName();
			final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
					+ ".sqlite_master WHERE type='index' AND name=?";
			String definition = null;
			try (var statement = connection.prepareStatement(sql)) {
				statement.setString(1, index.getName());
				try (var resultSet = statement.executeQuery()) {
					if (resultSet.next()) {
						definition = resultSet.getString(1);
						index.setWhere(extractWhere(definition));
						retainKeys(index, definition);
					}
				}
			}
			loadColumnDetails(connection, schemaName, index, definition);
		}
	}

	private void loadColumnDetails(final Connection connection, final String schemaName, final Index index,
			final String definition) throws SQLException {
		final String sql = "PRAGMA " + quoteIdentifier(schemaName) + ".index_xinfo(" + quoteString(index.getName())
				+ ")";
		final List<String> definitions = SqliteColumnReader.splitColumnDefinitions(definition);
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			final boolean hasDescending = hasColumn(resultSet, "desc");
			final boolean hasKey = hasColumn(resultSet, "key");
			index.getColumns().clear();
			while (resultSet.next()) {
				final String columnName = resultSet.getString("name");
				final int columnId = resultSet.getInt("cid");
				if (hasKey && !resultSet.getBoolean("key") || !hasKey && columnName == null && columnId != -2) {
					continue;
				}
				final Order order = hasDescending && resultSet.getBoolean("desc") ? Order.Desc : Order.Asc;
				if (columnName != null) {
					index.getColumns().add(new Column(columnName), order);
				} else {
					final int position = resultSet.getInt("seqno");
					if (position < definitions.size()) {
						index.getColumns().add(stripOrder(definitions.get(position)), order);
					}
				}
			}
		}
	}

	private void retainKeys(Index index, String definition) {
		List<String> keys = SqliteColumnReader.splitColumnDefinitions(definition);
		if (!keys.isEmpty()) index.getSpecifics().put("sqlite.index.keys", String.join(", ", keys));
	}

	static String stripOrder(final String definition) {
		return definition.replaceFirst("(?is)\\s+(?:ASC|DESC)\\s*$", "").trim();
	}

	private boolean hasColumn(final java.sql.ResultSet resultSet, final String name) throws SQLException {
		for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
			if (name.equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(i))) {
				return true;
			}
		}
		return false;
	}

	static String extractWhere(final String sql) {
		if (sql == null) {
			return null;
		}
		int depth = 0;
		char quote = 0;
		for (int i = 0; i < sql.length(); i++) {
			final char c = sql.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
						i++;
					} else {
						quote = 0;
					}
				}
			} else if (c == '\'' || c == '"' || c == '`' || c == '[') {
				quote = c == '[' ? ']' : c;
			} else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
				final int end = sql.indexOf('\n', i + 2);
				i = end < 0 ? sql.length() : end;
			} else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
				final int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? sql.length() : end + 1;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (depth == 0 && i > 0 && !Character.isJavaIdentifierPart(sql.charAt(i - 1))
					&& sql.regionMatches(true, i, "WHERE", 0, 5)
					&& i + 5 < sql.length() && !Character.isJavaIdentifierPart(sql.charAt(i + 5))) {
				return sql.substring(i + 5).trim();
			}
		}
		return null;
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String quoteString(final String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private List<String> tableNames(final ParametersContext context) {
		final List<String> result = list();
		final Object value = context.get(SchemaProperties.TABLE_NAME.getLabel());
		if (value instanceof Collection<?> collection) {
			collection.forEach(name -> result.add(String.valueOf(name)));
		} else if (value != null && value.getClass().isArray()) {
			for (int i = 0; i < Array.getLength(value); i++) {
				result.add(String.valueOf(Array.get(value, i)));
			}
		} else if (value != null) {
			result.add(String.valueOf(value));
		}
		return result;
	}
}
