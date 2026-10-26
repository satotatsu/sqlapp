/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Reads SQLite columns from table-valued PRAGMA metadata. */
public class SqliteColumnReader extends ColumnReader {
	private static final Pattern SIZE_PATTERN = Pattern
			.compile("^\\s*([^()]+?)(?:\\s*\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\s*\\))?\\s*$");

	public SqliteColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		if (SqliteIndexReader.supportsBulkIndexes(productVersionInfo)) {
			return readBulkColumns(connection, context, productVersionInfo);
		}
		List<Column> result = readColumns(connection, context, "table_xinfo");
		if (result.isEmpty()) {
			result = readColumns(connection, context, "table_info");
		}
		return result;
	}

	private List<Column> readBulkColumns(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Column> result = list();
		String pragma = version.getMajorVersion() > 3 || version.getMinorVersion() >= 26 ? "table_xinfo" : "table_info";
		Map<String, List<Column>> primaryKeys = new java.util.LinkedHashMap<>();
		Map<String, String> definitions = new java.util.LinkedHashMap<>();
		try (var statement = SqliteMetadataQueries.prepare(connection, context, "m.type AS object_type, p.*",
				"JOIN pragma_" + pragma + "(m.name, ?) p", ", p.cid", true); var rs = statement.executeQuery()) {
			while (rs.next()) {
				String table = rs.getString("table_name");
				String definition = rs.getString("table_sql");
				definitions.put(table, definition);
				Column column = new Column(rs.getString("name"));
				column.setDialect(getDialect());
				column.setCatalogName(getCatalogName(context));
				column.setSchemaName(getSchemaName(context));
				column.setTableName(table);
				splitColumnDefinitions(definition).stream()
						.filter(item -> Objects.equals(column.getName(), readIdentifier(item))).findFirst()
						.ifPresent(item -> setColumnDeclaration(column, item));
				setDataType(column, rs.getString("type"));
				setViewColumnType(column, "view".equals(rs.getString("object_type")));
				setStrictAnyType(column, definition);
				setVirtualColumnType(column, definition);
				int pk = rs.getInt("pk");
				column.setNullable(rs.getInt("notnull") == 0);
				column.setDefaultValue(rs.getString("dflt_value"));
				if (pk > 0)
					primaryKeys.computeIfAbsent(table, key -> new java.util.ArrayList<>()).add(column);
				int hidden = pragma.equals("table_xinfo") ? rs.getInt("hidden") : 0;
				column.setHidden(hidden == 1);
				column.setFormulaPersisted(hidden == 3);
				if (hidden == 2 || hidden == 3) {
					splitColumnDefinitions(definition).stream()
							.filter(item -> Objects.equals(column.getName(), readIdentifier(item)))
							.map(SqliteColumnReader::extractFormula).filter(Objects::nonNull).findFirst()
							.ifPresent(column::setFormula);
				}
				result.add(column);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		primaryKeys.forEach((table, keys) -> {
			if (keys.size() == 1 && "INTEGER"
					.equalsIgnoreCase(keys.get(0).getSpecifics().getOrDefault("sqlite.declaredType", "").trim()))
				setPrimaryKeyDetails(keys.get(0), definitions.get(table));
		});
		return result;
	}

	private static void setColumnDeclaration(Column column, String declaration) {
		String collation = SqliteDdl.columnCollation(declaration);
		if (collation != null)
			column.setCollation(collation);
		String conflict = SqliteDdl.notNullConflict(declaration);
		if (conflict != null)
			column.getSpecifics().put("sqlite.notNull.onConflict", conflict);
	}

	private static void setPrimaryKeyDetails(Column column, String definition) {
		boolean descending = splitColumnDefinitions(definition).stream()
				.filter(item -> Objects.equals(column.getName(), readIdentifier(item)))
				.anyMatch(item -> SqliteDdl.hasKeywords(item, "PRIMARY", "KEY", "DESC"));
		if (descending)
			column.getSpecifics().put("sqlite.primaryKey.inlineDesc", "true");
		if (!descending && !SqliteDdl.hasTableOption(definition, "WITHOUT", "ROWID")) {
			column.setIdentity(true);
			column.setNullable(false);
			if (splitColumnDefinitions(definition).stream()
					.filter(item -> Objects.equals(column.getName(), readIdentifier(item)))
					.anyMatch(item -> SqliteDdl.hasKeywords(item, "AUTOINCREMENT")))
				column.getSpecifics().put("sqlite.autoincrement", "true");
		}
	}

	private List<Column> readColumns(final Connection connection, final ParametersContext context,
			final String pragma) {
		final List<Column> result = list();
		final String tableName = getTableName(context);
		if (tableName == null) {
			return result;
		}
		final String schemaName = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final String sql = "PRAGMA " + quoteIdentifier(schemaName) + "." + pragma + "(" + quoteString(tableName) + ")";
		final Map<Column, Integer> primaryKeyPositions = new IdentityHashMap<>();
		final Map<Column, Integer> hiddenKinds = new IdentityHashMap<>();
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			final boolean hasHidden = hasColumn(resultSet, "hidden");
			while (resultSet.next()) {
				final Column column = new Column(resultSet.getString("name"));
				column.setDialect(getDialect());
				column.setCatalogName(getCatalogName(context));
				column.setSchemaName(getSchemaName(context));
				column.setTableName(tableName);
				setDataType(column, resultSet.getString("type"));
				final int primaryKeyPosition = resultSet.getInt("pk");
				column.setNullable(resultSet.getInt("notnull") == 0);
				column.setDefaultValue(resultSet.getString("dflt_value"));
				if (hasHidden) {
					final int hidden = resultSet.getInt("hidden");
					column.setHidden(hidden == 1);
					column.setFormulaPersisted(hidden == 3);
					hiddenKinds.put(column, hidden);
				}
				primaryKeyPositions.put(column, primaryKeyPosition);
				result.add(column);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		loadColumnDetails(connection, schemaName, tableName, hiddenKinds, primaryKeyPositions);
		return result;
	}

	private void loadColumnDetails(final Connection connection, final String schemaName, final String tableName,
			final Map<Column, Integer> hiddenKinds, final Map<Column, Integer> primaryKeyPositions) {
		final String sql = "SELECT sql, type FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_master WHERE type IN ('table','view') AND name=?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, tableName);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return;
				}
				String createSql = resultSet.getString(1);
				boolean view = "view".equals(resultSet.getString(2));
				primaryKeyPositions.keySet().forEach(column -> setViewColumnType(column, view));
				primaryKeyPositions.keySet().forEach(column -> setStrictAnyType(column, createSql));
				primaryKeyPositions.keySet().forEach(column -> setVirtualColumnType(column, createSql));
				final List<String> definitions = splitColumnDefinitions(createSql);
				long keyCount = primaryKeyPositions.values().stream().filter(position -> position > 0).count();
				primaryKeyPositions.forEach((column, position) -> {
					definitions.stream().filter(item -> Objects.equals(column.getName(), readIdentifier(item)))
							.findFirst().ifPresent(item -> setColumnDeclaration(column, item));
					if (keyCount == 1 && position > 0 && "INTEGER"
							.equalsIgnoreCase(column.getSpecifics().getOrDefault("sqlite.declaredType", "").trim()))
						setPrimaryKeyDetails(column, createSql);
				});
				hiddenKinds.forEach((column, kind) -> {
					if (kind != 2 && kind != 3) {
						return;
					}
					definitions.stream()
							.filter(definition -> Objects.equals(column.getName(), readIdentifier(definition)))
							.map(SqliteColumnReader::extractFormula).filter(Objects::nonNull).findFirst()
							.ifPresent(column::setFormula);
				});
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	static List<String> splitColumnDefinitions(final String createSql) {
		final List<String> result = list();
		if (createSql == null) {
			return result;
		}
		final int start = openingParenthesis(createSql);
		if (start < 0) {
			return result;
		}
		int depth = 1;
		int itemStart = start + 1;
		char quote = 0;
		for (int i = start + 1; i < createSql.length(); i++) {
			final char current = createSql.charAt(i);
			if (quote != 0) {
				if (current == quote || quote == ']' && current == ']') {
					if (quote != ']' && i + 1 < createSql.length() && createSql.charAt(i + 1) == quote) {
						i++;
					} else {
						quote = 0;
					}
				}
				continue;
			}
			if (current == '-' && i + 1 < createSql.length() && createSql.charAt(i + 1) == '-') {
				int end = createSql.indexOf('\n', i + 2);
				i = end < 0 ? createSql.length() : end;
			} else if (current == '/' && i + 1 < createSql.length() && createSql.charAt(i + 1) == '*') {
				int end = createSql.indexOf("*/", i + 2);
				i = end < 0 ? createSql.length() : end + 1;
			} else if (current == '\'' || current == '"' || current == '`') {
				quote = current;
			} else if (current == '[') {
				quote = ']';
			} else if (current == '(') {
				depth++;
			} else if (current == ')') {
				depth--;
				if (depth == 0) {
					result.add(createSql.substring(itemStart, i).trim());
					break;
				}
			} else if (current == ',' && depth == 1) {
				result.add(createSql.substring(itemStart, i).trim());
				itemStart = i + 1;
			}
		}
		return result;
	}

	private static int openingParenthesis(String sql) {
		char quote = 0;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					if (quote != ']' && i + 1 < sql.length() && sql.charAt(i + 1) == quote)
						i++;
					else
						quote = 0;
				}
			} else if (c == '\'' || c == '"' || c == '`' || c == '[') {
				quote = c == '[' ? ']' : c;
			} else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
				int end = sql.indexOf('\n', i + 2);
				i = end < 0 ? sql.length() : end;
			} else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
				int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? sql.length() : end + 1;
			} else if (c == '(')
				return i;
		}
		return -1;
	}

	static String extractFormula(final String definition) {
		return SqliteDdl.generatedFormula(definition);
	}

	private static String readIdentifier(final String definition) {
		return SqliteDdl.firstIdentifier(definition);
	}

	private boolean hasColumn(final java.sql.ResultSet resultSet, final String name) throws SQLException {
		for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
			if (name.equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(i))) {
				return true;
			}
		}
		return false;
	}

	private static void setVirtualColumnType(Column column, String definition) {
		String module = SqliteDdl.virtualTableModule(definition);
		if (module != null && java.util.Set.of("fts3", "fts4", "fts5").contains(module.toLowerCase(Locale.ROOT))
				&& hasEmptyDeclaredType(column))
			column.setDataType(DataType.VARCHAR).setDataTypeName(null);
	}

	private static void setStrictAnyType(Column column, String definition) {
		if ("ANY".equalsIgnoreCase(column.getSpecifics().get("sqlite.declaredType"))
				&& SqliteDdl.hasTableOption(definition, "STRICT"))
			column.setDataType(DataType.ANY_DATA).setDataTypeName(null);
	}

	private static void setViewColumnType(Column column, boolean view) {
		String declared = column.getSpecifics().get("sqlite.declaredType");
		// A view's undeclared expression can yield any storage class per row.
		// SQLite may report a projected STRICT ANY as BLOB, so BLOB views also
		// need pass-through conversion; genuine binary values remain byte[].
		if (view && (hasEmptyDeclaredType(column) || "ANY".equalsIgnoreCase(declared)
				|| "BLOB".equalsIgnoreCase(declared)))
			column.setDataType(DataType.ANY_DATA).setDataTypeName(null).setLength(null).setScale(null);
	}

	private static boolean hasEmptyDeclaredType(Column column) {
		return "true".equals(column.getSpecifics().get("sqlite.declaredType.empty"))
				|| "".equals(column.getSpecifics().get("sqlite.declaredType"));
	}

	private void setDataType(final Column column, final String declaredType) {
		final String typeName = declaredType == null ? "" : declaredType;
		if (typeName.isEmpty())
			column.getSpecifics().put("sqlite.declaredType.empty", "true");
		else
			column.getSpecifics().put("sqlite.declaredType", typeName);
		final Matcher matcher = SIZE_PATTERN.matcher(typeName);
		final String baseType = matcher.matches() ? matcher.group(1).trim() : typeName;
		final Long precision = matcher.matches() && matcher.group(2) != null ? Long.valueOf(matcher.group(2)) : null;
		final Integer scale = matcher.matches() && matcher.group(3) != null ? Integer.valueOf(matcher.group(3)) : null;
		int sqlType = toSqlType(baseType);
		// Ordinary ANY retains NUMERIC affinity; only STRICT ANY is dynamic.
		getDialect().setDbType(sqlType, "ANY".equalsIgnoreCase(baseType) ? "NUMERIC" : baseType, precision, scale,
				column);
		// SQLite accepts arbitrary declarations; map unregistered names by affinity
		// for JDBC binding while sqlite.declaredType preserves executable source DDL.
		if (column.getDataType() == null || column.getDataType().isOther()) {
			column.setDataTypeName(null);
			column.setDataType(DataType.valueOf(sqlType));
			if (precision != null)
				column.setLength(precision);
			if (scale != null)
				column.setScale(scale);
		}
	}

	private int toSqlType(final String declaredType) {
		final String type = declaredType.toUpperCase(Locale.ROOT);
		if (type.contains("INT")) {
			return Types.BIGINT;
		}
		if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) {
			return Types.VARCHAR;
		}
		if (type.contains("BLOB") || type.isEmpty()) {
			return Types.BLOB;
		}
		if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) {
			return Types.DOUBLE;
		}
		return Types.NUMERIC;
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String quoteString(final String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
