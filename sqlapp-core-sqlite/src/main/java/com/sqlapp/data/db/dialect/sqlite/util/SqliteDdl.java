/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Header handling that keeps quoted identifiers, strings and comments opaque.
 */
public final class SqliteDdl {
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
			return definition.substring(0, tokens.get(first).start()) + identity
					+ definition.substring(tokens.get(last).end());
		}
		throw new IllegalArgumentException("SQLite CREATE " + kind + " header is missing");
	}

	private static String quote(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}
