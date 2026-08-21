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
	private static final Pattern SIZE_PATTERN = Pattern.compile(
			"^\\s*([^()]+?)(?:\\s*\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\s*\\))?\\s*$");
	private static final Pattern AS_PATTERN = Pattern.compile("(?i)\\bAS\\s*\\(");

	public SqliteColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Column> result = readColumns(connection, context, "table_xinfo");
		if (result.isEmpty()) {
			result = readColumns(connection, context, "table_info");
		}
		return result;
	}

	private List<Column> readColumns(final Connection connection,
			final ParametersContext context, final String pragma) {
		final List<Column> result = list();
		final String tableName = getTableName(context);
		if (tableName == null) {
			return result;
		}
		final String schemaName = getSchemaName(context) == null
				? "main" : getSchemaName(context);
		final String sql = "PRAGMA " + quoteIdentifier(schemaName) + "."
				+ pragma + "(" + quoteString(tableName) + ")";
		final Map<Column, Integer> primaryKeyPositions = new IdentityHashMap<>();
		final Map<Column, Integer> hiddenKinds = new IdentityHashMap<>();
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			final boolean hasHidden = hasColumn(resultSet, "hidden");
			while (resultSet.next()) {
				final Column column = new Column(resultSet.getString("name"));
				column.setDialect(getDialect());
				column.setCatalogName(getCatalogName(context));
				column.setSchemaName(getSchemaName(context));
				column.setTableName(tableName);
				setDataType(column, resultSet.getString("type"));
				final int primaryKeyPosition = resultSet.getInt("pk");
				column.setNullable(resultSet.getInt("notnull") == 0
						&& primaryKeyPosition == 0);
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
		final long primaryKeyColumns = primaryKeyPositions.values().stream()
				.filter(position -> position > 0).count();
		if (primaryKeyColumns == 1) {
			primaryKeyPositions.forEach((column, position) -> {
				if (position > 0 && "INTEGER".equalsIgnoreCase(
						column.getDataTypeName())) {
					column.setIdentity(true);
				}
			});
		}
		loadGeneratedExpressions(connection, schemaName, tableName, hiddenKinds);
		return result;
	}

	private void loadGeneratedExpressions(final Connection connection,
			final String schemaName, final String tableName,
			final Map<Column, Integer> hiddenKinds) {
		if (hiddenKinds.values().stream().noneMatch(kind -> kind == 2 || kind == 3)) {
			return;
		}
		final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_schema WHERE type='table' AND name=?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, tableName);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return;
				}
				final List<String> definitions = splitColumnDefinitions(
						resultSet.getString(1));
				hiddenKinds.forEach((column, kind) -> {
					if (kind != 2 && kind != 3) {
						return;
					}
					definitions.stream()
							.filter(definition -> Objects.equals(column.getName(),
									readIdentifier(definition)))
							.map(SqliteColumnReader::extractFormula)
							.filter(Objects::nonNull).findFirst()
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
		final int start = createSql.indexOf('(');
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
					if (quote != ']' && i + 1 < createSql.length()
							&& createSql.charAt(i + 1) == quote) {
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
				if (current == quote && (i + 1 >= definition.length()
						|| definition.charAt(i + 1) != quote)) {
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
			final int endIndex = value.indexOf(end, 1);
			return endIndex < 0 ? value : value.substring(1, endIndex);
		}
		final int whitespace = value.indexOf(' ');
		return whitespace < 0 ? value : value.substring(0, whitespace);
	}

	private boolean hasColumn(final java.sql.ResultSet resultSet,
			final String name) throws SQLException {
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
		final Long precision = matcher.matches() && matcher.group(2) != null
				? Long.valueOf(matcher.group(2)) : null;
		final Integer scale = matcher.matches() && matcher.group(3) != null
				? Integer.valueOf(matcher.group(3)) : null;
		getDialect().setDbType(toSqlType(baseType), baseType, precision, scale,
				column);
	}

	private int toSqlType(final String declaredType) {
		final String type = declaredType.toUpperCase(Locale.ROOT);
		if (type.contains("INT")) {
			return Types.BIGINT;
		}
		if (type.contains("CHAR") || type.contains("CLOB")
				|| type.contains("TEXT")) {
			return Types.VARCHAR;
		}
		if (type.contains("BLOB") || type.isEmpty()) {
			return Types.BLOB;
		}
		if (type.contains("REAL") || type.contains("FLOA")
				|| type.contains("DOUB")) {
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
