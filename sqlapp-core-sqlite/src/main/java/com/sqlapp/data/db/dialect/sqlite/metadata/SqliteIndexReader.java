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
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;

/**
 * Reads indexes created by CREATE INDEX in one query: table-valued PRAGMAs on
 * SQLite 3.16+, the parsed catalog SQL on older versions.
 */
public class SqliteIndexReader extends JdbcIndexReader {

	public SqliteIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		try {
			if (supportsBulkIndexes(productVersionInfo)) {
				return readBulkIndexes(connection, context);
			}
			return readLegacyIndexes(connection, context);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	static boolean supportsBulkIndexes(final ProductVersionInfo version) {
		return version.getMajorVersion() != null && (version.getMajorVersion() > 3 || version.getMajorVersion() == 3
				&& version.getMinorVersion() != null && version.getMinorVersion() >= 16);
	}

	private List<Index> readBulkIndexes(final Connection connection, final ParametersContext context)
			throws SQLException {
		final Map<String, Index> indexes = new LinkedHashMap<>();
		final List<String> tables = tableNames(context);
		final String requestedIndex = getObjectName(context);
		final String schema = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final Map<String, List<String>> expressionKeys = new java.util.HashMap<>();
		// Bounded name sets stay below SQLite's historical 999-parameter limit.
		for (List<String> chunk : SqliteMetadataQueries.chunks(tables)) {
			final String sql = "SELECT m.name AS table_name, il.name AS index_name, il.\"unique\" AS is_unique, "
					+ "d.sql AS definition, x.* FROM " + quoteIdentifier(schema) + ".sqlite_master m "
					+ "JOIN pragma_index_list(m.name, ?) il JOIN pragma_index_xinfo(il.name, ?) x " + "LEFT JOIN "
					+ quoteIdentifier(schema) + ".sqlite_master d ON d.type='index' AND d.name=il.name "
					// UNIQUE/PRIMARY KEY constraints are read as constraints; their internal
					// sqlite_autoindex_* indexes cannot be created or dropped directly.
					+ "WHERE m.type='table' AND il.origin='c' AND x.key=1"
					+ (chunk.isEmpty() ? ""
							: " AND m.name IN (" + String.join(",", java.util.Collections.nCopies(chunk.size(), "?"))
									+ ")")
					+ (requestedIndex == null ? "" : " AND il.name=?") + " ORDER BY m.name, il.name, x.seqno";
			try (var statement = connection.prepareStatement(sql)) {
				statement.setString(1, schema);
				statement.setString(2, schema);
				for (int i = 0; i < chunk.size(); i++) {
					statement.setString(i + 3, chunk.get(i));
				}
				if (requestedIndex != null)
					statement.setString(chunk.size() + 3, requestedIndex);
				try (var rs = statement.executeQuery()) {
					while (rs.next()) {
						final String name = rs.getString("index_name");
						Index index = indexes.get(name);
						if (index == null) {
							index = new Index(name);
							index.setDialect(getDialect());
							index.setCatalogName(getCatalogName(context));
							index.setSchemaName(schema);
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
							// Parse each expression index once, not once per expression key.
							List<String> keys = expressionKeys.get(name);
							if (keys == null) {
								keys = SqliteColumnReader.splitColumnDefinitions(rs.getString("definition"));
								expressionKeys.put(name, keys);
							}
							index.getColumns().add(stripOrder(keys.get(rs.getInt("seqno"))), order);
						}
					}
				}
			}
		}
		return new java.util.ArrayList<>(indexes.values());
	}

	private List<Index> readLegacyIndexes(final Connection connection, final ParametersContext context)
			throws SQLException {
		final String schema = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final List<String> tables = tableNames(context);
		final String requestedIndex = getObjectName(context);
		final Map<Index, String> definitions = new LinkedHashMap<>();
		// Bounded name sets stay below SQLite's historical 999-parameter limit.
		for (List<String> chunk : SqliteMetadataQueries.chunks(tables)) {
			// Internal constraint indexes are the ones without CREATE SQL.
			final String sql = "SELECT name, tbl_name, sql FROM " + quoteIdentifier(schema)
					+ ".sqlite_master WHERE type='index' AND sql IS NOT NULL"
					+ (chunk.isEmpty() ? ""
							: " AND tbl_name IN (" + String.join(",", java.util.Collections.nCopies(chunk.size(), "?"))
									+ ")")
					+ (requestedIndex == null ? "" : " AND name=?") + " ORDER BY tbl_name, name";
			try (var statement = connection.prepareStatement(sql)) {
				for (int i = 0; i < chunk.size(); i++)
					statement.setString(i + 1, chunk.get(i));
				if (requestedIndex != null)
					statement.setString(chunk.size() + 1, requestedIndex);
				try (var rows = statement.executeQuery()) {
					while (rows.next()) {
						var index = new Index(rows.getString("name"));
						String definition = rows.getString("sql");
						index.setDialect(getDialect());
						index.setCatalogName(getCatalogName(context));
						index.setSchemaName(schema);
						index.setTableName(rows.getString("tbl_name"));
						index.setUnique(SqliteDdl.isUniqueIndex(definition));
						index.setWhere(extractWhere(definition));
						retainKeys(index, definition);
						definitions.put(index, definition);
					}
				}
			}
		}
		for (var entry : definitions.entrySet()) {
			var keys = SqliteColumnReader.splitColumnDefinitions(entry.getValue());
			if (keys.isEmpty())
				throw new IllegalArgumentException("SQLite index definition has no key: " + entry.getKey().getName());
			for (String key : keys) {
				Order order = SqliteDdl.isDescendingIndexKey(key) ? Order.Desc : Order.Asc;
				if (SqliteDdl.hasIndexExpressions(key))
					entry.getKey().getColumns().add(stripOrder(key), order);
				else
					entry.getKey().getColumns().add(new Column(SqliteDdl.firstIdentifier(key)), order);
			}
		}
		return new java.util.ArrayList<>(definitions.keySet());
	}

	private void retainKeys(Index index, String definition) {
		List<String> keys = SqliteColumnReader.splitColumnDefinitions(definition);
		if (!keys.isEmpty())
			index.getSpecifics().put(SqliteDdl.INDEX_KEYS, String.join(", ", keys));
	}

	static String stripOrder(final String definition) {
		return SqliteDdl.stripIndexKeyOrder(definition);
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
					&& sql.regionMatches(true, i, "WHERE", 0, 5) && i + 5 < sql.length()
					&& !Character.isJavaIdentifierPart(sql.charAt(i + 5))) {
				return sql.substring(i + 5).trim();
			}
		}
		return null;
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
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
