/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.util.Locale;

/**
 * Reads only the declaration of an Oracle {@code CREATE TRIGGER} statement.
 * <p>
 * Used when the trigger type was not recorded by the metadata reader. It skips
 * comments, string literals (including {@code q'[...]'}), quoted identifiers
 * and parenthesised conditions, and stops at the first top-level
 * {@code DECLARE} or {@code BEGIN}, so words in the trigger body never decide
 * the form.
 */
final class OracleTriggerHeader {
	private OracleTriggerHeader() {
	}

	/**
	 * Whether the declaration contains {@code COMPOUND TRIGGER} at the top level
	 * before the body starts.
	 */
	static boolean declaresCompound(final String definition) {
		if (definition == null) {
			return false;
		}
		final String sql = definition;
		final int length = sql.length();
		int depth = 0;
		String previous = null;
		int i = 0;
		while (i < length) {
			final char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
				final int end = sql.indexOf('\n', i);
				i = end < 0 ? length : end + 1;
			} else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
				final int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? length : end + 2;
			} else if (c == '"') {
				final int end = sql.indexOf('"', i + 1);
				i = end < 0 ? length : end + 1;
			} else if (c == '\'') {
				i = skipLiteral(sql, i);
			} else if ((c == 'q' || c == 'Q') && i + 2 < length && sql.charAt(i + 1) == '\'') {
				i = skipQuoted(sql, i + 2);
			} else if ((c == 'n' || c == 'N') && i + 3 < length
					&& (sql.charAt(i + 1) == 'q' || sql.charAt(i + 1) == 'Q') && sql.charAt(i + 2) == '\'') {
				i = skipQuoted(sql, i + 3);
			} else if (c == '(') {
				depth++;
				i++;
			} else if (c == ')') {
				depth = Math.max(0, depth - 1);
				i++;
			} else if (isWordChar(c)) {
				int end = i + 1;
				while (end < length && isWordChar(sql.charAt(end))) {
					end++;
				}
				if (depth == 0) {
					final String word = sql.substring(i, end).toUpperCase(Locale.ROOT);
					if ("BEGIN".equals(word) || "DECLARE".equals(word)) {
						return false;
					}
					if ("TRIGGER".equals(word) && "COMPOUND".equals(previous)) {
						return true;
					}
					previous = word;
				}
				i = end;
			} else {
				i++;
			}
		}
		return false;
	}

	/**
	 * Skips {@code '...'} with doubled quotes; {@code start} is the opening quote.
	 */
	private static int skipLiteral(final String sql, final int start) {
		int i = start + 1;
		while (i < sql.length()) {
			if (sql.charAt(i) == '\'') {
				if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
					i += 2;
					continue;
				}
				return i + 1;
			}
			i++;
		}
		return sql.length();
	}

	/**
	 * Skips an alternative quoting body; {@code start} is the delimiter after
	 * {@code q'}.
	 */
	private static int skipQuoted(final String sql, final int start) {
		final char open = sql.charAt(start);
		final char close = switch (open) {
		case '[' -> ']';
		case '(' -> ')';
		case '{' -> '}';
		case '<' -> '>';
		default -> open;
		};
		final int end = sql.indexOf(close + "'", start + 1);
		return end < 0 ? sql.length() : end + 2;
	}

	private static boolean isWordChar(final char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
	}
}
