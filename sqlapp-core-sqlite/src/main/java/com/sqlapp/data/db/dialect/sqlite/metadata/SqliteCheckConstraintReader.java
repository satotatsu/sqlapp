/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Reads SQLite CHECK constraints from the stored CREATE TABLE statement. */
public class SqliteCheckConstraintReader extends CheckConstraintReader {
	private static final Pattern CHECK_PATTERN = Pattern.compile("(?i)\\bCHECK\\s*\\(");
	private static final Pattern NAME_PATTERN = Pattern.compile(
			"(?i)\\bCONSTRAINT\\s+(?:\"([^\"]+)\"|`([^`]+)`|\\[([^]]+)]|([^\\s]+))\\s+CHECK\\s*\\(");

	public SqliteCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<CheckConstraint> result = list();
		final String tableName = getTableName(context);
		if (tableName == null) {
			return result;
		}
		final String schemaName = getSchemaName(context) == null
				? "main" : getSchemaName(context);
		final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_master WHERE type='table' AND name=?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, tableName);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return result;
				}
				int sequence = 0;
				for (String definition : SqliteColumnReader
						.splitColumnDefinitions(resultSet.getString(1))) {
					for (String expression : extractExpressions(definition)) {
						sequence++;
						final String declaredName = extractName(definition);
						final CheckConstraint constraint = new CheckConstraint(
								declaredName == null
										? "sqlite_check_" + tableName + "_" + sequence
										: declaredName,
								expression);
						constraint.setDialect(getDialect());
						constraint.setCatalogName(getCatalogName(context));
						constraint.setSchemaName(getSchemaName(context));
						constraint.setTableName(tableName);
						result.add(constraint);
					}
				}
				return result;
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	static List<String> extractExpressions(final String definition) {
		final List<String> result = list();
		final Matcher matcher = CHECK_PATTERN.matcher(definition);
		int from = 0;
		while (matcher.find(from)) {
			final int open = matcher.end() - 1;
			final int close = matchingParenthesis(definition, open);
			if (close < 0) {
				break;
			}
			result.add(definition.substring(open + 1, close).trim());
			from = close + 1;
		}
		return result;
	}

	private static int matchingParenthesis(final String text, final int open) {
		int depth = 1;
		char quote = 0;
		for (int i = open + 1; i < text.length(); i++) {
			final char current = text.charAt(i);
			if (quote != 0) {
				if (current == quote && (i + 1 >= text.length()
						|| text.charAt(i + 1) != quote)) {
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
				return i;
			}
		}
		return -1;
	}

	private static String extractName(final String definition) {
		final Matcher matcher = NAME_PATTERN.matcher(definition);
		if (!matcher.find()) {
			return null;
		}
		for (int i = 1; i <= matcher.groupCount(); i++) {
			if (matcher.group(i) != null) {
				return matcher.group(i);
			}
		}
		return null;
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
