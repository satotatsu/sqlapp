/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Header handling that keeps quoted identifiers, strings and comments opaque.
 */
public final class SqliteDdl {
	/** Virtual-table module arguments are not ordinary column declarations. */
	public static boolean isVirtualTable(String definition) {
		if (definition == null)
			return false;
		var items = tokens(definition);
		return items.size() > 2 && items.get(0).is("CREATE") && items.get(1).is("VIRTUAL") && items.get(2).is("TABLE");
	}

	/**
	 * Module name from a CREATE VIRTUAL TABLE header, without interpreting its
	 * arguments.
	 */
	public static String virtualTableModule(String definition) {
		if (!isVirtualTable(definition))
			return null;
		var items = tokens(definition);
		int first = 3;
		if (first + 2 < items.size() && items.get(first).is("IF") && items.get(first + 1).is("NOT")
				&& items.get(first + 2).is("EXISTS"))
			first += 3;
		int after = first + 1;
		if (after + 1 < items.size() && items.get(after).is("."))
			after += 2;
		return after + 1 < items.size() && items.get(after).is("USING") ? identifier(items.get(after + 1).text())
				: null;
	}

	/** Native module column names, excluding key=value module options. */
	public static List<String> virtualTableColumns(String definition) {
		var result = new ArrayList<String>();
		for (var argument : virtualArguments(definition)) {
			var items = tokens(argument);
			if (items.size() > 1 && items.get(1).is("="))
				continue;
			if (items.isEmpty())
				throw new IllegalArgumentException("Empty virtual-table argument");
			int name = items.get(0).is("+") ? 1 : 0;
			if (name >= items.size())
				throw new IllegalArgumentException("Missing virtual-table column name");
			result.add(identifier(items.get(name).text()));
		}
		return List.copyOf(result);
	}

	/** Scalar module options with quoted strings decoded; columns are omitted. */
	public static java.util.Map<String, String> virtualTableOptions(String definition) {
		var result = new java.util.LinkedHashMap<String, String>();
		for (var argument : virtualArguments(definition)) {
			var items = tokens(argument);
			if (items.size() < 2 || !items.get(1).is("="))
				continue;
			if (items.size() != 3)
				throw new IllegalArgumentException("Expected a scalar virtual-table option: " + argument);
			String key = identifier(items.get(0).text()).toLowerCase(java.util.Locale.ROOT);
			if (result.putIfAbsent(key, identifier(items.get(2).text())) != null)
				throw new IllegalArgumentException("Duplicate virtual-table option: " + key);
		}
		return java.util.Map.copyOf(result);
	}

	private static List<String> virtualArguments(String definition) {
		if (!isVirtualTable(definition))
			throw new IllegalArgumentException("Expected CREATE VIRTUAL TABLE definition");
		var items = tokens(definition);
		int open = 0;
		while (open < items.size() && !items.get(open).is("USING"))
			open++;
		open += 2;
		if (open >= items.size() || !items.get(open).is("("))
			throw new IllegalArgumentException("Expected virtual-table module arguments");
		var result = new ArrayList<String>();
		int start = items.get(open).end(), depth = 0;
		for (int i = open + 1; i < items.size(); i++) {
			var item = items.get(i);
			if (item.is(")") && depth == 0) {
				result.add(definition.substring(start, item.start()));
				return result;
			}
			if (item.is(",") && depth == 0) {
				result.add(definition.substring(start, item.start()));
				start = item.end();
			} else if (item.is("("))
				depth++;
			else if (item.is(")"))
				depth--;
		}
		throw new IllegalArgumentException("Unclosed virtual-table module arguments");
	}

	/** CREATE INDEX header, with comments and quoted names kept opaque. */
	public static boolean isUniqueIndex(String definition) {
		var items = tokens(definition);
		return items.size() > 2 && items.get(0).is("CREATE") && items.get(1).is("UNIQUE") && items.get(2).is("INDEX");
	}

	/** Explicit order of an index key, ignoring trailing comments. */
	public static boolean isDescendingIndexKey(String definition) {
		var items = tokens(definition);
		return hasIndexOrder(items) && items.get(items.size() - 1).is("DESC");
	}

	/** Removes only a final ASC/DESC modifier, including any trailing comments. */
	public static String stripIndexKeyOrder(String definition) {
		var items = tokens(definition);
		return hasIndexOrder(items) ? definition.substring(0, items.get(items.size() - 1).start()).trim()
				: definition.trim();
	}

	private static boolean hasIndexOrder(List<Token> items) {
		return items.size() > 1 && !items.get(items.size() - 2).is("COLLATE")
				&& (items.get(items.size() - 1).is("ASC") || items.get(items.size() - 1).is("DESC"));
	}

	/** Distinguishes identifier/COLLATE/order index keys from expression keys. */
	public static boolean hasIndexExpressions(String keys) {
		var items = tokens(keys);
		for (int i = 0; i < items.size();) {
			String first = items.get(i++).text();
			char c = first.charAt(0);
			if (!(Character.isLetter(c) || c == '_' || c == '"' || c == '[' || c == '`'))
				return true;
			if (i < items.size() && items.get(i).is("COLLATE"))
				i += 2;
			if (i < items.size() && (items.get(i).is("ASC") || items.get(i).is("DESC")))
				i++;
			if (i < items.size() && !items.get(i++).is(","))
				return true;
		}
		return false;
	}

	/** First identifier in a column fragment, after any leading comments. */
	public static String firstIdentifier(String fragment) {
		var items = tokens(fragment);
		return items.isEmpty() ? "" : identifier(items.get(0).text());
	}

	/**
	 * Generated AS expression, ignoring text in comments, literals and nested
	 * expressions.
	 */
	public static String generatedFormula(String fragment) {
		var items = tokens(fragment);
		int depth = 0;
		for (int i = 1; i + 1 < items.size(); i++) {
			Token token = items.get(i);
			if (token.is("("))
				depth++;
			else if (token.is(")"))
				depth--;
			if (depth == 0 && token.is("AS") && items.get(i + 1).is("(")) {
				int close = closingToken(items, i + 1);
				return fragment.substring(items.get(i + 1).end(), items.get(close).start()).trim();
			}
		}
		return null;
	}

	public record KeyColumn(String name, String collation, boolean descending) {
	}

	public record KeyDeclaration(String name, boolean primary, List<KeyColumn> columns, String conflict) {
	}

	public record CheckDeclaration(String name, String expression) {
	}

	/**
	 * Reads PRIMARY KEY and UNIQUE constraints from one table-definition fragment.
	 */
	public static List<KeyDeclaration> keys(String fragment) {
		List<Token> items = tokens(fragment);
		List<KeyDeclaration> result = new ArrayList<>();
		int start = !items.isEmpty() && items.get(0).is("CONSTRAINT") ? 2 : 0;
		int depth = 0;
		for (int i = 0; i < items.size(); i++) {
			Token token = items.get(i);
			if (token.is("("))
				depth++;
			else if (token.is(")"))
				depth--;
			boolean primary = token.is("PRIMARY") && i + 1 < items.size() && items.get(i + 1).is("KEY");
			if (depth != 0 || !(primary || token.is("UNIQUE")))
				continue;
			int next = i + (primary ? 2 : 1);
			List<KeyColumn> columns = new ArrayList<>();
			if (i == start && next < items.size() && items.get(next).is("(")) {
				int close = closingToken(items, next);
				for (int j = next + 1; j < close;) {
					String column = identifier(items.get(j++).text());
					String collation = null;
					boolean descending = false;
					if (j + 1 < close && items.get(j).is("COLLATE")) {
						collation = identifier(items.get(j + 1).text());
						j += 2;
					}
					if (j < close && (items.get(j).is("ASC") || items.get(j).is("DESC")))
						descending = items.get(j++).is("DESC");
					columns.add(new KeyColumn(column, collation, descending));
					if (j < close && !items.get(j++).is(","))
						throw new IllegalArgumentException("Unsupported SQLite key syntax: " + fragment);
				}
				next = close + 1;
			} else {
				boolean descending = next < items.size() && items.get(next).is("DESC");
				if (next < items.size() && (items.get(next).is("ASC") || items.get(next).is("DESC")))
					next++;
				columns.add(new KeyColumn(identifier(items.get(0).text()), null, descending));
			}
			String conflict = next + 2 < items.size() && items.get(next).is("ON") && items.get(next + 1).is("CONFLICT")
					? items.get(next + 2).text().toUpperCase(java.util.Locale.ROOT)
					: null;
			result.add(new KeyDeclaration(constraintName(items, i), primary, List.copyOf(columns), conflict));
		}
		return result;
	}

	/** Reads each CHECK with its own name; comments and literals remain opaque. */
	public static List<CheckDeclaration> checks(String fragment) {
		List<Token> items = tokens(fragment);
		List<CheckDeclaration> result = new ArrayList<>();
		int depth = 0;
		for (int i = 0; i + 1 < items.size(); i++) {
			Token token = items.get(i);
			if (token.is("("))
				depth++;
			else if (token.is(")"))
				depth--;
			if (depth != 0 || !token.is("CHECK") || !items.get(i + 1).is("("))
				continue;
			int close = closingToken(items, i + 1);
			result.add(new CheckDeclaration(constraintName(items, i),
					fragment.substring(items.get(i + 1).end(), items.get(close).start()).trim()));
			i = close;
		}
		return result;
	}

	private static String constraintName(List<Token> items, int position) {
		return position >= 2 && items.get(position - 2).is("CONSTRAINT") ? identifier(items.get(position - 1).text())
				: null;
	}

	private static int closingToken(List<Token> items, int open) {
		int depth = 0;
		for (int i = open; i < items.size(); i++) {
			if (items.get(i).is("("))
				depth++;
			else if (items.get(i).is(")") && --depth == 0)
				return i;
		}
		throw new IllegalArgumentException("Unclosed SQLite constraint expression");
	}

	/** Catalog attributes unavailable from foreign_key_list. */
	public record ForeignKeyDeclaration(String name, List<String> columns, String parent, List<String> parentColumns,
			com.sqlapp.data.schemas.CascadeRule updateRule, com.sqlapp.data.schemas.CascadeRule deleteRule,
			com.sqlapp.data.schemas.Deferrability deferrability) {
	}

	/** Parses foreign keys in one column or table-constraint fragment. */
	public static List<ForeignKeyDeclaration> foreignKeys(String fragment) {
		List<Token> items = tokens(fragment);
		List<ForeignKeyDeclaration> result = new ArrayList<>();
		if (items.isEmpty())
			return result;
		int start = items.get(0).is("CONSTRAINT") ? 2 : 0;
		boolean tableKey = start + 1 < items.size() && items.get(start).is("FOREIGN") && items.get(start + 1).is("KEY");
		List<String> columns = tableKey ? identifierList(items, start + 2) : List.of(identifier(items.get(0).text()));
		int depth = 0;
		for (int i = 0; i < items.size(); i++) {
			Token token = items.get(i);
			if (token.is("("))
				depth++;
			else if (token.is(")"))
				depth--;
			if (depth != 0 || !token.is("REFERENCES"))
				continue;
			if (i + 1 >= items.size())
				throw new IllegalArgumentException("SQLite REFERENCES requires a parent table");
			String name = tableKey && start == 2 ? identifier(items.get(1).text())
					: i >= 2 && items.get(i - 2).is("CONSTRAINT") ? identifier(items.get(i - 1).text()) : null;
			String parent = identifier(items.get(i + 1).text());
			List<String> parentColumns = i + 2 < items.size() && items.get(i + 2).is("(") ? identifierList(items, i + 2)
					: List.of();
			int end = i + 2;
			int nested = 0;
			for (; end < items.size(); end++) {
				Token next = items.get(end);
				if (next.is("("))
					nested++;
				else if (next.is(")"))
					nested--;
				if (nested == 0 && (next.is("CONSTRAINT") || next.is("REFERENCES")))
					break;
			}
			var update = com.sqlapp.data.schemas.CascadeRule.None;
			var delete = com.sqlapp.data.schemas.CascadeRule.None;
			var deferrability = com.sqlapp.data.schemas.Deferrability.NotDeferrable;
			boolean deferrable = false;
			boolean deferred = false;
			nested = 0;
			for (int j = i + 2; j < end; j++) {
				Token part = items.get(j);
				if (part.is("("))
					nested++;
				else if (part.is(")"))
					nested--;
				if (nested != 0)
					continue;
				if (part.is("DEFERRABLE"))
					deferrable = j == 0 || !items.get(j - 1).is("NOT");
				if (part.is("INITIALLY") && j + 1 < end)
					deferred = items.get(j + 1).is("DEFERRED");
				if (part.is("ON") && j + 2 < end) {
					String action = items.get(j + 2).text();
					if ((items.get(j + 2).is("SET") || items.get(j + 2).is("NO")) && j + 3 < end)
						action += " " + items.get(j + 3).text();
					var rule = com.sqlapp.data.schemas.CascadeRule.parse(action);
					if (items.get(j + 1).is("UPDATE"))
						update = rule;
					if (items.get(j + 1).is("DELETE"))
						delete = rule;
				}
			}
			if (deferrable)
				deferrability = deferred ? com.sqlapp.data.schemas.Deferrability.InitiallyDeferred
						: com.sqlapp.data.schemas.Deferrability.InitiallyImmediate;
			result.add(new ForeignKeyDeclaration(name, columns, parent, parentColumns, update, delete, deferrability));
		}
		return result;
	}

	private static List<String> identifierList(List<Token> items, int open) {
		if (open >= items.size() || !items.get(open).is("("))
			throw new IllegalArgumentException("SQLite foreign key requires a column list");
		List<String> columns = new ArrayList<>();
		for (int i = open + 1; i < items.size(); i++) {
			if (items.get(i).is(")"))
				return List.copyOf(columns);
			if (!items.get(i).is(","))
				columns.add(identifier(items.get(i).text()));
		}
		throw new IllegalArgumentException("Unclosed SQLite foreign-key column list");
	}

	private static String identifier(String name) {
		char first = name.charAt(0);
		if (first == '"' || first == '`' || first == '[' || first == '\'') {
			String quote = first == '[' ? "]" : String.valueOf(first);
			return name.substring(1, name.length() - 1).replace(quote + quote, quote);
		}
		return name;
	}

	private SqliteDdl() {
	}

	private record Token(String text, int start, int end) {
		boolean is(String keyword) {
			return text.equalsIgnoreCase(keyword);
		}
	}

	private static List<Token> tokens(String sql) {
		List<Token> result = new ArrayList<>();
		for (int i = 0; i < sql.length();) {
			char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if (sql.startsWith("--", i)) {
				int end = sql.indexOf('\n', i + 2);
				i = end < 0 ? sql.length() : end + 1;
				continue;
			}
			if (sql.startsWith("/*", i)) {
				int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? sql.length() : end + 2;
				continue;
			}
			int start = i++;
			if (c == '\'' || c == '"' || c == '`' || c == '[') {
				char quote = c == '[' ? ']' : c;
				while (i < sql.length()) {
					if (sql.charAt(i++) == quote) {
						if (quote != ']' && i < sql.length() && sql.charAt(i) == quote)
							i++;
						else
							break;
					}
				}
			} else if (Character.isLetterOrDigit(c) || c == '_') {
				while (i < sql.length()
						&& (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$'))
					i++;
			}
			result.add(new Token(sql.substring(start, i), start, i));
		}
		return result;
	}

	/** Returns the query after the unquoted AS in a CREATE VIEW header. */
	public static String viewStatement(String definition) {
		if (definition == null)
			return null;
		for (Token token : tokens(definition)) {
			if (token.is("AS"))
				return definition.substring(token.end()).trim();
		}
		throw new IllegalArgumentException("SQLite CREATE VIEW requires AS: " + definition);
	}

	/**
	 * Returns timing and event from the trigger header, with SQLite's default
	 * BEFORE timing.
	 */
	public static List<String> triggerEvent(String definition) {
		List<Token> tokens = tokens(definition);
		for (int i = 0; i < tokens.size(); i++) {
			if (!tokens.get(i).is("TRIGGER"))
				continue;
			int event = i + 2;
			if (i + 3 < tokens.size() && tokens.get(i + 1).is("IF") && tokens.get(i + 2).is("NOT")
					&& tokens.get(i + 3).is("EXISTS"))
				event += 3;
			if (event + 1 < tokens.size() && tokens.get(event).is("."))
				event += 2;
			String timing = "BEFORE";
			if (event < tokens.size() && (tokens.get(event).is("BEFORE") || tokens.get(event).is("AFTER")))
				timing = tokens.get(event++).text().toUpperCase(java.util.Locale.ROOT);
			else if (event + 1 < tokens.size() && tokens.get(event).is("INSTEAD") && tokens.get(event + 1).is("OF")) {
				timing = "INSTEAD OF";
				event += 2;
			}
			if (event < tokens.size() && (tokens.get(event).is("INSERT") || tokens.get(event).is("UPDATE")
					|| tokens.get(event).is("DELETE")))
				return List.of(timing, tokens.get(event).text().toUpperCase(java.util.Locale.ROOT));
			break;
		}
		throw new IllegalArgumentException("SQLite trigger event is missing");
	}

	/**
	 * Returns an explicitly qualified ON schema, or null for an unqualified target.
	 */
	public static String triggerTargetSchema(String definition) {
		List<Token> items = tokens(definition);
		int target = triggerTargetPosition(items);
		return target + 2 < items.size() && items.get(target + 1).is(".") ? identifier(items.get(target).text()) : null;
	}

	/** Returns the decoded table/view name from the trigger's ON declaration. */
	public static String triggerTargetName(String definition) {
		List<Token> items = tokens(definition);
		int target = triggerTargetPosition(items);
		if (target + 2 < items.size() && items.get(target + 1).is("."))
			target += 2;
		return identifier(items.get(target).text());
	}

	/**
	 * Returns modeled UPDATE OF identifiers; SQLite itself permits nonexistent
	 * names.
	 */
	public static List<String> triggerUpdateColumns(String definition) {
		List<Token> items = tokens(definition);
		int on = triggerTargetPosition(items) - 1;
		for (int position = 0; position + 1 < on; position++) {
			if (items.get(position).is("UPDATE") && items.get(position + 1).is("OF"))
				return items.subList(position + 2, on).stream().filter(token -> !token.is(","))
						.map(token -> identifier(token.text())).toList();
		}
		return List.of();
	}

	/**
	 * Qualifies an unqualified ON target without changing an explicit target or
	 * body.
	 */
	public static String qualifyTriggerTarget(String definition, String schema) {
		if (schema == null || schema.isBlank() || schema.indexOf('\0') >= 0)
			throw new IllegalArgumentException("SQLite trigger target schema is required and must not contain NUL");
		List<Token> items = tokens(definition);
		int target = triggerTargetPosition(items);
		if (target + 2 < items.size() && items.get(target + 1).is("."))
			return definition;
		int position = items.get(target).start();
		return definition.substring(0, position) + quote(schema) + "." + definition.substring(position);
	}

	private static int triggerTargetPosition(List<Token> items) {
		int start = 0;
		while (start < items.size() && !items.get(start).is("TRIGGER"))
			start++;
		start += 2;
		if (start < items.size() && items.get(start - 1).is("IF"))
			start += 3;
		if (start < items.size() && items.get(start).is("."))
			start += 2;
		for (int i = start; i < items.size(); i++) {
			if (items.get(i).is("ON") && i + 1 < items.size()) {
				return i + 1;
			}
		}
		throw new IllegalArgumentException("SQLite trigger ON target is missing");
	}

	/**
	 * Replaces only the declared object identity, preserving the executable DDL.
	 */
	public static String withIdentity(String definition, String kind, String schema, String name) {
		List<Token> tokens = tokens(definition);
		for (int i = 0; i < tokens.size(); i++) {
			if (!tokens.get(i).is(kind))
				continue;
			int first = i + 1;
			if (first + 2 < tokens.size() && tokens.get(first).is("IF") && tokens.get(first + 1).is("NOT")
					&& tokens.get(first + 2).is("EXISTS"))
				first += 3;
			if (first >= tokens.size())
				break;
			int last = first;
			if (first + 2 < tokens.size() && tokens.get(first + 1).is("."))
				last += 2;
			String identity = (schema == null ? "" : quote(schema) + ".") + quote(name);
			String prefix = definition.substring(0, tokens.get(first).start());
			// A qualified name already selects the owning database. SQLite rejects
			// CREATE TEMP TRIGGER temp.name (and the equivalent TEMP VIEW spelling).
			if (schema != null && tokens.size() > 1 && tokens.get(0).is("CREATE")
					&& (tokens.get(1).is("TEMP") || tokens.get(1).is("TEMPORARY")))
				prefix = definition.substring(0, tokens.get(1).start())
						+ definition.substring(tokens.get(1).end(), tokens.get(first).start());
			return prefix + identity + definition.substring(tokens.get(last).end());
		}
		throw new IllegalArgumentException("SQLite CREATE " + kind + " header is missing");
	}

	private static String quote(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	/**
	 * Reads a column-level COLLATE, excluding nested expressions and the column
	 * name.
	 */
	public static String columnCollation(String definition) {
		List<Token> items = tokens(definition);
		int depth = 0;
		for (int i = 1; i + 1 < items.size(); i++) {
			Token token = items.get(i);
			if (token.is("("))
				depth++;
			else if (token.is(")"))
				depth--;
			else if (depth == 0 && token.is("COLLATE")) {
				String name = items.get(i + 1).text();
				char first = name.charAt(0);
				if (first == '"' || first == '`' || first == '[' || first == '\'') {
					String quote = first == '[' ? "]" : String.valueOf(first);
					return name.substring(1, name.length() - 1).replace(quote + quote, quote);
				}
				return name;
			}
		}
		return null;
	}

	/**
	 * Reads a column-level NOT NULL conflict policy, excluding nested expressions.
	 */
	public static String notNullConflict(String definition) {
		List<Token> items = tokens(definition);
		int depth = 0;
		for (int i = 1; i + 4 < items.size(); i++) {
			Token token = items.get(i);
			if (token.is("("))
				depth++;
			else if (token.is(")"))
				depth--;
			if (depth == 0 && token.is("NOT") && items.get(i + 1).is("NULL") && items.get(i + 2).is("ON")
					&& items.get(i + 3).is("CONFLICT"))
				return items.get(i + 4).text().toUpperCase(java.util.Locale.ROOT);
		}
		return null;
	}

	/** Matches consecutive unquoted keywords, ignoring whitespace and comments. */
	public static boolean hasKeywords(String sql, String... keywords) {
		if (sql == null)
			return false;
		List<Token> tokens = tokens(sql);
		for (int i = 0; i + keywords.length <= tokens.size(); i++) {
			boolean match = true;
			for (int j = 0; j < keywords.length; j++)
				match &= tokens.get(i + j).is(keywords[j]);
			if (match)
				return true;
		}
		return false;
	}

	/** Matches options after the closing table-definition parenthesis. */
	public static boolean hasTableOption(String sql, String... keywords) {
		if (sql == null)
			return false;
		int depth = 0;
		boolean started = false;
		for (Token token : tokens(sql)) {
			if (token.is("(")) {
				started = true;
				depth++;
			} else if (token.is(")") && started && --depth == 0)
				return hasKeywords(sql.substring(token.end()), keywords);
		}
		return false;
	}
}
