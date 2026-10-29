/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import io.github.spannm.jackcess.DataType;

/**
 * Bounded saved SELECT grammar; quoting and nesting are handled before
 * splitting clauses.
 */
final class MdbSelectQueryParser {
	private static final String ID = "(?:\\[(?:[^\\]]|\\]\\])+\\]|[\\p{L}\\p{N}_$]+)";
	private static final Pattern TABLE = Pattern.compile("(?is)^(" + ID + ")(?:\\s+(?:AS\\s+)?(" + ID + "))?$");

	record Parameter(String name, short type, Integer length) {
	}

	record Definition(String sql, List<Parameter> parameters) {
	}

	record Table(String name, String alias) {
		String key() {
			return alias == null ? name : unquote(alias);
		}
	}

	record Join(String left, String right, short type, String condition) {
	}

	private record From(List<Table> tables, List<Join> joins) {
		String key() {
			return tables.getFirst().key();
		}
	}

	record Select(boolean distinct, String columns, List<Table> tables, List<Join> joins, String where, String group,
			String having, String order) {
	}

	static Definition definition(final String definition) {
		String sql = definition.strip();
		if (sql.endsWith(";"))
			sql = sql.substring(0, sql.length() - 1).stripTrailing();
		final List<Parameter> parameters = new ArrayList<>();
		final var header = Pattern.compile("(?is)^PARAMETERS\\s+").matcher(sql);
		if (header.find()) {
			final int separator = delimiter(sql, ';');
			if (separator < 0)
				throw unsupported("PARAMETERS requires a terminating semicolon");
			for (final String item : splitComma(sql.substring(header.end(), separator))) {
				final var matcher = Pattern.compile("(?is)^(" + ID + ")\\s+([A-Z_]+)(?:\\s*\\(\\s*(\\d+)\\s*\\))?$")
						.matcher(item.strip());
				if (!matcher.matches())
					throw unsupported("Invalid parameter declaration: " + item);
				final DataType type = parameterType(matcher.group(2));
				final Integer length = matcher.group(3) == null ? null : Integer.valueOf(matcher.group(3));
				if (length != null && (type != DataType.TEXT || length < 1 || length > 255))
					throw unsupported("Only TEXT parameters accept a length from 1 to 255");
				if (parameters.stream().anyMatch(p -> unquote(p.name()).equalsIgnoreCase(unquote(matcher.group(1)))))
					throw unsupported("Duplicate parameter: " + matcher.group(1));
				parameters.add(new Parameter(matcher.group(1), (short) type.getValue(), length));
			}
			sql = sql.substring(separator + 1).strip();
		}
		if (delimiter(sql, ';') >= 0)
			throw unsupported("Only one saved SELECT statement is accepted");
		return new Definition(sql, List.copyOf(parameters));
	}

	static Select select(final String sql) {
		if (!sql.regionMatches(true, 0, "SELECT ", 0, 7) && !sql.matches("(?is)^SELECT\\s+.*"))
			throw unsupported("Only SELECT and two-part UNION queries are supported");
		if (keyword(sql, "UNION") >= 0 || keyword(sql, "INTO") >= 0 || keyword(sql, "WITH OWNERACCESS OPTION") >= 0)
			throw unsupported("Unsupported SELECT clause");
		final int from = keyword(sql, "FROM");
		if (from < 0)
			throw unsupported("Saved SELECT requires FROM");
		String columns = sql.substring(6, from).strip();
		final boolean distinct = columns.matches("(?is)^DISTINCT\\s+.*");
		if (distinct)
			columns = columns.replaceFirst("(?is)^DISTINCT\\s+", "");
		if (columns.isEmpty() || columns.matches("(?is)^(TOP|DISTINCTROW)\\b.*"))
			throw unsupported("Unsupported SELECT projection");
		final String[] clauses = { "WHERE", "GROUP BY", "HAVING", "ORDER BY" };
		final String[] values = new String[4];
		final int[] positions = new int[4];
		int endFrom = sql.length();
		int previous = from;
		for (int i = 0; i < clauses.length; i++) {
			positions[i] = keyword(sql, clauses[i]);
			if (positions[i] >= 0) {
				if (positions[i] < previous)
					throw unsupported("SELECT clauses are out of order");
				previous = positions[i];
				endFrom = Math.min(endFrom, positions[i]);
			}
		}
		for (int i = 0; i < clauses.length; i++) {
			if (positions[i] < 0)
				continue;
			int end = sql.length();
			for (int j = i + 1; j < clauses.length; j++)
				if (positions[j] >= 0) {
					end = positions[j];
					break;
				}
			final var matcher = Pattern.compile("(?is)" + clauses[i].replace(" ", "\\s+")).matcher(sql);
			matcher.find(positions[i]);
			values[i] = sql.substring(matcher.end(), end).strip();
			if (values[i].isEmpty())
				throw unsupported("Empty " + clauses[i] + " clause");
		}
		if (values[2] != null && values[1] == null)
			throw unsupported("HAVING requires GROUP BY");
		final From source = from(sql.substring(from + 4, endFrom).strip(), 0);
		final var names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		for (final Table table : source.tables()) {
			if (!names.add(table.key()))
				throw unsupported("JOIN tables require distinct names or aliases: " + table.key());
		}
		return new Select(distinct, columns, source.tables(), source.joins(), values[0], values[1], values[2],
				values[3]);
	}

	private static From from(String source, final int depth) {
		if (depth > 64)
			throw unsupported("Saved-query JOIN nesting exceeds 64 levels");
		while (source.startsWith("(") && source.endsWith(")")) {
			final boolean[] top = topLevel(source);
			boolean contentOutside = false;
			for (int i = 0; i < source.length(); i++) {
				if (top[i] && !Character.isWhitespace(source.charAt(i))) {
					contentOutside = true;
					break;
				}
			}
			if (contentOutside)
				break;
			source = source.substring(1, source.length() - 1).strip();
		}
		final var matcher = Pattern.compile("(?is)\\b(INNER|LEFT|RIGHT)\\s+(?:OUTER\\s+)?JOIN\\b").matcher(source);
		final boolean[] top = topLevel(source);
		int joinStart = -1;
		int joinEnd = -1;
		short joinType = 0;
		while (matcher.find()) {
			if (top[matcher.start()]) {
				joinStart = matcher.start();
				joinEnd = matcher.end();
				joinType = (short) switch (matcher.group(1).toUpperCase(Locale.ROOT)) {
				case "INNER" -> 1;
				case "LEFT" -> 2;
				default -> 3;
				};
			}
		}
		if (joinStart < 0)
			return new From(List.of(table(source)), List.of());
		final int relativeOn = keyword(source.substring(joinEnd), "ON");
		if (relativeOn < 0)
			throw unsupported("JOIN requires ON");
		final int on = joinEnd + relativeOn;
		final From left = from(source.substring(0, joinStart).strip(), depth + 1);
		final From right = from(source.substring(joinEnd, on).strip(), depth + 1);
		final String condition = source.substring(on + 2).strip();
		if (condition.isEmpty())
			throw unsupported("JOIN requires a non-empty ON condition");
		final List<Table> tables = new ArrayList<>(left.tables());
		tables.addAll(right.tables());
		final List<Join> joins = new ArrayList<>(left.joins());
		joins.addAll(right.joins());
		joins.add(new Join(left.key(), right.key(), joinType, condition));
		return new From(List.copyOf(tables), List.copyOf(joins));
	}

	static int keyword(final String sql, final String phrase) {
		final boolean[] top = topLevel(sql);
		final var matcher = Pattern
				.compile("(?is)(?<![\\p{L}\\p{N}_$])" + phrase.replace(" ", "\\s+") + "(?![\\p{L}\\p{N}_$])")
				.matcher(sql);
		while (matcher.find())
			if (top[matcher.start()])
				return matcher.start();
		return -1;
	}

	static List<String> splitComma(final String sql) {
		final boolean[] top = topLevel(sql);
		final List<String> result = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < sql.length(); i++)
			if (top[i] && sql.charAt(i) == ',') {
				result.add(sql.substring(start, i).strip());
				start = i + 1;
			}
		result.add(sql.substring(start).strip());
		if (result.stream().anyMatch(String::isEmpty))
			throw unsupported("Empty list item");
		return result;
	}

	private static int delimiter(final String sql, final char delimiter) {
		final boolean[] top = topLevel(sql);
		for (int i = 0; i < sql.length(); i++)
			if (top[i] && sql.charAt(i) == delimiter)
				return i;
		return -1;
	}

	private static boolean[] topLevel(final String sql) {
		final boolean[] result = new boolean[sql.length()];
		int depth = 0;
		char quote = 0;
		for (int i = 0; i < sql.length(); i++) {
			final char c = sql.charAt(i);
			if (quote != 0) {
				final char end = quote == '[' ? ']' : quote;
				if (c == end) {
					if (i + 1 < sql.length() && sql.charAt(i + 1) == end && quote != '#')
						i++;
					else
						quote = 0;
				}
				continue;
			}
			if (c == '\'' || c == '"' || c == '[' || c == '#') {
				quote = c;
				continue;
			}
			if (c == '(') {
				depth++;
				continue;
			}
			if (c == ')') {
				if (--depth < 0)
					throw unsupported("Unbalanced parentheses");
				continue;
			}
			if (i + 1 < sql.length() && (sql.startsWith("--", i) || sql.startsWith("/*", i)))
				throw unsupported("SQL comments are not supported in saved-query definitions");
			result[i] = depth == 0;
		}
		if (quote != 0 || depth != 0)
			throw unsupported("Unterminated quoting or parentheses");
		return result;
	}

	private static Table table(final String source) {
		final var matcher = TABLE.matcher(source);
		if (!matcher.matches())
			throw unsupported("Expected one table with optional alias: " + source);
		return new Table(unquote(matcher.group(1)), matcher.group(2) == null ? null : unquote(matcher.group(2)));
	}

	static String unquote(final String name) {
		return name.startsWith("[") ? name.substring(1, name.length() - 1).replace("]]", "]") : name;
	}

	private static DataType parameterType(final String type) {
		return switch (type.toUpperCase(Locale.ROOT)) {
		case "TEXT", "VARCHAR" -> DataType.TEXT;
		case "SHORT", "INTEGER", "SMALLINT" -> DataType.INT;
		case "LONG", "INT" -> DataType.LONG;
		case "BYTE" -> DataType.BYTE;
		case "DATETIME", "DATE" -> DataType.SHORT_DATE_TIME;
		case "YESNO", "BOOLEAN", "BIT" -> DataType.BOOLEAN;
		case "CURRENCY", "MONEY" -> DataType.MONEY;
		case "SINGLE", "REAL" -> DataType.FLOAT;
		case "DOUBLE" -> DataType.DOUBLE;
		case "GUID" -> DataType.GUID;
		default -> throw unsupported("Unsupported parameter type: " + type);
		};
	}

	private static UnsupportedOperationException unsupported(final String message) {
		return new UnsupportedOperationException(message);
	}
}
