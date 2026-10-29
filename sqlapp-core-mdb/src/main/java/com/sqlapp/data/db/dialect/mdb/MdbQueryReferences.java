/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Name resolution for expressions in the deliberately bounded saved-query
 * grammar.
 */
final class MdbQueryReferences {
	private static final String ID = "(?:\\[(?:[^\\]]|\\]\\])+\\]|[\\p{L}_$][\\p{L}\\p{N}_$]*)";
	private static final Pattern TOKEN = Pattern.compile(ID + "|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|\\S");
	private static final Pattern ALIAS = Pattern.compile("(?is)^(.*?)\\s+AS\\s+(" + ID + ")$");
	private static final Set<String> WORDS = Set.of("AND", "OR", "NOT", "IS", "NULL", "TRUE", "FALSE", "LIKE",
			"BETWEEN", "IN", "ASC", "DESC", "DISTINCT", "MOD", "XOR", "EQV", "IMP", "CASE", "WHEN", "THEN", "ELSE",
			"END");

	private MdbQueryReferences() {
	}

	static void validate(final MdbSelectQueryParser.Select select, final Map<String, Set<String>> tables,
			final List<MdbSelectQueryParser.Parameter> parameters) {
		final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (final var parameter : parameters)
			names.add(MdbSelectQueryParser.unquote(parameter.name()));
		final List<String> projections = new ArrayList<>();
		final Set<String> aliases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (final String projection : MdbSelectQueryParser.splitComma(select.columns())) {
			final var matcher = ALIAS.matcher(projection);
			if (matcher.matches()) {
				projections.add(matcher.group(1));
				aliases.add(MdbSelectQueryParser.unquote(matcher.group(2)));
			} else
				projections.add(projection);
		}
		for (final String projection : projections)
			expression(projection, tables, names);
		for (final var join : select.joins())
			expression(join.condition(), tables, names);
		if (select.where() != null)
			expression(select.where(), tables, names);
		if (select.group() != null)
			expression(select.group(), tables, names);
		if (select.having() != null)
			expression(select.having(), tables, names);
		names.addAll(aliases);
		if (select.order() != null)
			expression(select.order(), tables, names);
	}

	private static void expression(final String expression, final Map<String, Set<String>> tables,
			final Set<String> parameters) {
		final var matcher = TOKEN.matcher(withoutLiterals(expression));
		final List<String> tokens = new ArrayList<>();
		while (matcher.find())
			tokens.add(matcher.group());
		for (int i = 0; i < tokens.size(); i++) {
			final String token = tokens.get(i);
			if (token.equals("!"))
				throw invalid("Use table.column references instead of '!'");
			if (!token.startsWith("[") && !Character.isLetter(token.charAt(0)) && token.charAt(0) != '_'
					&& token.charAt(0) != '$')
				continue;
			final String name = MdbSelectQueryParser.unquote(token);
			if (i + 1 < tokens.size() && tokens.get(i + 1).equals(".")) {
				if (i + 2 >= tokens.size())
					throw invalid("Incomplete qualified column: " + token);
				final String column = MdbSelectQueryParser.unquote(tokens.get(i + 2));
				final Set<String> columns = tables.get(name);
				if (columns == null || !column.equals("*") && !columns.contains(column))
					throw invalid("Unknown qualified column: " + token + "." + tokens.get(i + 2));
				i += 2;
				continue;
			}
			if (i + 1 < tokens.size() && tokens.get(i + 1).equals("("))
				continue;
			if (!token.startsWith("[") && WORDS.contains(name.toUpperCase(Locale.ROOT)))
				continue;
			if (parameters.contains(name))
				continue;
			int matches = 0;
			for (final var columns : tables.values())
				if (columns.contains(name))
					matches++;
			if (matches != 1)
				throw invalid((matches == 0 ? "Unknown" : "Ambiguous") + " column: " + token);
		}
	}

	private static String withoutLiterals(final String expression) {
		final StringBuilder result = new StringBuilder(expression);
		char quote = 0;
		boolean bracket = false;
		for (int i = 0; i < expression.length(); i++) {
			final char c = expression.charAt(i);
			if (quote != 0) {
				result.setCharAt(i, ' ');
				if (c == quote) {
					if (quote != '#' && i + 1 < expression.length() && expression.charAt(i + 1) == quote)
						result.setCharAt(++i, ' ');
					else
						quote = 0;
				}
			} else if (bracket) {
				if (c == ']') {
					if (i + 1 < expression.length() && expression.charAt(i + 1) == ']')
						i++;
					else
						bracket = false;
				}
			} else if (c == '[')
				bracket = true;
			else if (c == '\'' || c == '"' || c == '#') {
				quote = c;
				result.setCharAt(i, ' ');
			}
		}
		return result.toString();
	}

	static Set<String> columnNames() {
		return new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	}

	static Map<String, Set<String>> tables() {
		return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	}

	private static IllegalArgumentException invalid(final String message) {
		return new IllegalArgumentException("Saved query: " + message);
	}
}
