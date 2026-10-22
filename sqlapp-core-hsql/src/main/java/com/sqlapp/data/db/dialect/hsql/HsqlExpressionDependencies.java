/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Identifier-only analysis of HSQLDB expressions; SQL text is never rewritten.
 */
final class HsqlExpressionDependencies {
	private HsqlExpressionDependencies() {
	}

	record Reference(boolean sequence, List<String> name) {
	}

	private record Token(String text, boolean identifier) {
	}

	/**
	 * Conservative type-use discovery in generated DDL; literals and comments are
	 * excluded.
	 */
	static boolean mentionsIdentifier(String sql, String schema, String name) {
		var tokens = tokenize(sql);
		for (int i = 0; i < tokens.size(); i++)
			if (tokens.get(i).identifier() && tokens.get(i).text().equals(name)) {
				if (i == 0 || !tokens.get(i - 1).text().equals("."))
					return true;
				if (i >= 2 && tokens.get(i - 2).identifier() && tokens.get(i - 2).text().equals(schema))
					return true;
			}
		return false;
	}

	static List<Reference> references(String expression) {
		if (expression == null || expression.isBlank())
			return List.of();
		var tokens = tokenize(expression);
		var result = new ArrayList<Reference>();
		for (int i = 0; i < tokens.size(); i++) {
			if (!tokens.get(i).identifier() || i > 0 && tokens.get(i - 1).text().equals("."))
				continue;
			var parts = new ArrayList<String>();
			parts.add(tokens.get(i).text());
			int end = i + 1;
			while (end + 1 < tokens.size() && tokens.get(end).text().equals(".") && tokens.get(end + 1).identifier()) {
				parts.add(tokens.get(end + 1).text());
				end += 2;
			}
			boolean sequence = i >= 3 && tokens.get(i - 1).text().equals("FOR")
					&& tokens.get(i - 2).text().equals("VALUE")
					&& (tokens.get(i - 3).text().equals("NEXT") || tokens.get(i - 3).text().equals("CURRENT"));
			if (sequence || end < tokens.size() && tokens.get(end).text().equals("(")) {
				result.add(new Reference(sequence, List.copyOf(parts)));
			}
		}
		return result;
	}

	static List<String> typeName(String text) {
		if (text == null)
			return List.of();
		var tokens = tokenize(text);
		var names = new ArrayList<String>();
		for (int i = 0; i < tokens.size(); i++) {
			if (i % 2 == 0 && tokens.get(i).identifier())
				names.add(tokens.get(i).text());
			else if (i % 2 == 0 || !tokens.get(i).text().equals("."))
				return List.of();
		}
		return tokens.size() % 2 == 1 ? names : List.of();
	}

	private static List<Token> tokenize(String sql) {
		var tokens = new ArrayList<Token>();
		for (int i = 0; i < sql.length();) {
			char ch = sql.charAt(i);
			if (Character.isWhitespace(ch)) {
				i++;
				continue;
			}
			if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
				while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r')
					i++;
				continue;
			}
			if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
				int depth = 1;
				i += 2;
				while (i < sql.length() && depth > 0) {
					if (i + 1 < sql.length() && sql.startsWith("/*", i)) {
						depth++;
						i += 2;
					} else if (i + 1 < sql.length() && sql.startsWith("*/", i)) {
						depth--;
						i += 2;
					} else
						i++;
				}
				if (depth != 0)
					throw new IllegalArgumentException("Unclosed HSQLDB expression comment");
				continue;
			}
			if (ch == '\'' || ch == '"') {
				char quote = ch;
				var value = new StringBuilder();
				i++;
				boolean closed = false;
				while (i < sql.length()) {
					ch = sql.charAt(i++);
					if (ch != quote) {
						value.append(ch);
						continue;
					}
					if (i < sql.length() && sql.charAt(i) == quote) {
						value.append(ch);
						i++;
					} else {
						closed = true;
						break;
					}
				}
				if (!closed)
					throw new IllegalArgumentException("Unclosed HSQLDB expression quote");
				tokens.add(new Token(quote == '"' ? value.toString() : "<literal>", quote == '"'));
				continue;
			}
			if (Character.isJavaIdentifierStart(ch)) {
				int start = i++;
				while (i < sql.length() && Character.isJavaIdentifierPart(sql.charAt(i)))
					i++;
				tokens.add(new Token(sql.substring(start, i).toUpperCase(Locale.ROOT), true));
			} else {
				tokens.add(new Token(String.valueOf(ch), false));
				i++;
			}
		}
		return tokens;
	}
}
