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
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Reads SQLite columns from table-valued PRAGMA metadata. */
public class SqliteColumnReader extends ColumnReader {
	private static final Pattern SIZE_PATTERN = Pattern
			.compile("^\\s*([^()]+?)(?:\\s*\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\s*\\))?\\s*$");
	private static final Pattern AS_PATTERN = Pattern.compile("(?i)\\bAS\\s*\\(");

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
		try (var statement = SqliteMetadataQueries.prepare(connection, context, "p.*",
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
				setDataType(column, rs.getString("type"));
				int pk = rs.getInt("pk");
				column.setNullable(rs.getInt("notnull") == 0 && pk == 0);
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
			if (keys.size() == 1 && "INTEGER".equalsIgnoreCase(keys.get(0).getDataTypeName())
					&& isRowIdAlias(definitions.get(table), keys.get(0).getName()))
				keys.get(0).setIdentity(true);
		});
		return result;
	}

	private static boolean isRowIdAlias(String definition, String column) {
		if (definition == null)
			return true;
		String normalized = definition.toUpperCase(Locale.ROOT);
		String options = normalized.substring(normalized.lastIndexOf(')') + 1);
		return !options.matches("(?s).*\\bWITHOUT\\s+ROWID\\b.*") && splitColumnDefinitions(definition).stream()
				.filter(item -> Objects.equals(column, readIdentifier(item)))
				.noneMatch(item -> item.matches("(?is).*\\bPRIMARY\\s+KEY\\s+DESC\\b.*"));
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
				column.setNullable(resultSet.getInt("notnull") == 0 && primaryKeyPosition == 0);
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
		final long primaryKeyColumns = primaryKeyPositions.values().stream().filter(position -> position > 0).count();
		if (primaryKeyColumns == 1) {
			primaryKeyPositions.forEach((column, position) -> {
				if (position > 0 && "INTEGER".equalsIgnoreCase(column.getDataTypeName())
						&& isRowIdAlias(connection, schemaName, tableName, column.getName())) {
					column.setIdentity(true);
				}
			});
		}
		loadGeneratedExpressions(connection, schemaName, tableName, hiddenKinds);
		return result;
	}

	private boolean isRowIdAlias(final Connection connection, final String schemaName, final String tableName,
			final String columnName) {
		final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_master WHERE type='table' AND name=?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, tableName);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return true;
				}
				final String definition = resultSet.getString(1);
				if (definition == null) {
					return true;
				}
				final String normalized = definition.toUpperCase(Locale.ROOT);
				final int closingParenthesis = normalized.lastIndexOf(')');
				final String options = closingParenthesis < 0 ? "" : normalized.substring(closingParenthesis + 1);
				if (options.matches("(?s).*\\bWITHOUT\\s+ROWID\\b.*")) {
					return false;
				}
				return splitColumnDefinitions(definition).stream()
						.filter(item -> Objects.equals(columnName, readIdentifier(item)))
						.noneMatch(item -> item.matches("(?is).*\\bPRIMARY\\s+KEY\\s+DESC\\b.*"));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadGeneratedExpressions(final Connection connection, final String schemaName, final String tableName,
			final Map<Column, Integer> hiddenKinds) {
		if (hiddenKinds.values().stream().noneMatch(kind -> kind == 2 || kind == 3)) {
			return;
		}
		final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_master WHERE type='table' AND name=?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, tableName);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return;
				}
				final List<String> definitions = splitColumnDefinitions(resultSet.getString(1));
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
			if (current == '\'' || current == '"' || current == '`') {
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
		final Matcher matcher = AS_PATTERN.matcher(definition);
		if (!matcher.find()) {
			return null;
		}
		final int open = matcher.end() - 1;
		int depth = 1;
		char quote = 0;
		for (int i = open + 1; i < definition.length(); i++) {
			final char current = definition.charAt(i);
			if (quote != 0) {
				if (current == quote && (i + 1 >= definition.length() || definition.charAt(i + 1) != quote)) {
					quote = 0;
				} else if (current == quote) {
					i++;
				}
				continue;
			}
			if (current == '\'' || current == '"' || current == '`') {
				quote = current;
			} else if (current == '(') {
				depth++;
			} else if (current == ')' && --depth == 0) {
				return definition.substring(open + 1, i).trim();
			}
		}
		return null;
	}

	private static String readIdentifier(final String definition) {
		final String value = definition.trim();
		if (value.isEmpty()) {
			return value;
		}
		final char first = value.charAt(0);
		if (first == '"' || first == '`' || first == '[') {
			final char end = first == '[' ? ']' : first;
			StringBuilder identifier = new StringBuilder();
			for (int i = 1; i < value.length(); i++) {
				char current = value.charAt(i);
				if (current == end) {
					if (end != ']' && i + 1 < value.length() && value.charAt(i + 1) == end)
						i++;
					else
						return identifier.toString();
				}
				identifier.append(current);
			}
			return value;
		}
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i)))
				return value.substring(0, i);
		}
		return value;
	}

	private boolean hasColumn(final java.sql.ResultSet resultSet, final String name) throws SQLException {
		for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
			if (name.equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(i))) {
				return true;
			}
		}
		return false;
	}

	private void setDataType(final Column column, final String declaredType) {
		final String typeName = declaredType == null ? "" : declaredType;
		final Matcher matcher = SIZE_PATTERN.matcher(typeName);
		final String baseType = matcher.matches() ? matcher.group(1).trim() : typeName;
		final Long precision = matcher.matches() && matcher.group(2) != null ? Long.valueOf(matcher.group(2)) : null;
		final Integer scale = matcher.matches() && matcher.group(3) != null ? Integer.valueOf(matcher.group(3)) : null;
		getDialect().setDbType(toSqlType(baseType), baseType, precision, scale, column);
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
