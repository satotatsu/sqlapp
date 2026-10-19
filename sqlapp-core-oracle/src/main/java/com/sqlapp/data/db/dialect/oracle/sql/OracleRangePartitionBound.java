/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

/** Only a leading MAXVALUE makes a lexicographic RANGE boundary unbounded. */
final class OracleRangePartitionBound {
	private OracleRangePartitionBound() {
	}

	static boolean isUnbounded(String text) {
		boolean found = false;
		boolean leading = true;
		for (int i = 0; i < text.length();) {
			char c = text.charAt(i);
			if (c == '\'' || c == '"') {
				i = quotedEnd(text, i, c);
				leading = false;
			} else if (text.startsWith("--", i)) {
				i += 2;
				while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r')
					i++;
			} else if (text.startsWith("/*", i)) {
				int end = text.indexOf("*/", i + 2);
				if (end < 0)
					throw new IllegalArgumentException("Unterminated RANGE boundary comment");
				i = end + 2;
			} else if (Character.isLetter(c) || c == '_' || c == '$' || c == '#') {
				int start = i++;
				while (i < text.length()
						&& (Character.isLetterOrDigit(text.charAt(i)) || "_$#".indexOf(text.charAt(i)) >= 0))
					i++;
				String word = text.substring(start, i);
				if (("Q".equalsIgnoreCase(word) || "NQ".equalsIgnoreCase(word)) && i < text.length()
						&& text.charAt(i) == '\'') {
					if (i + 1 >= text.length())
						throw new IllegalArgumentException("Unterminated RANGE boundary alternative quote");
					int open = text.codePointAt(i + 1);
					int close = switch (open) {
					case '[' -> ']';
					case '(' -> ')';
					case '{' -> '}';
					case '<' -> '>';
					default -> open;
					};
					int end = text.indexOf(new String(Character.toChars(close)) + '\'',
							i + 1 + Character.charCount(open));
					if (end < 0)
						throw new IllegalArgumentException("Unterminated RANGE boundary alternative quote");
					i = end + Character.charCount(close) + 1;
				} else if (leading && "MAXVALUE".equalsIgnoreCase(word))
					found = true;
				leading = false;
			} else {
				if (!Character.isWhitespace(c))
					leading = false;
				i++;
			}
		}
		return found;
	}

	private static int quotedEnd(String text, int start, char quote) {
		for (int i = start + 1; i < text.length(); i++)
			if (text.charAt(i) == quote) {
				if (i + 1 < text.length() && text.charAt(i + 1) == quote)
					i++;
				else
					return i + 1;
			}
		throw new IllegalArgumentException("Unterminated RANGE boundary quote");
	}
}
