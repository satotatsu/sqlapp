/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.util.SqlTokenizer;

/**
 * A rows contain ordered ALTER statements without mandatory semicolon
 * separators.
 */
final class InformixRoutineAlterSql extends SqlTokenizer {
	private InformixRoutineAlterSql(String text) {
		super(text);
	}

	static List<String> split(String text) {
		if (text == null)
			return List.of();
		if (text.isBlank())
			throw new IllegalArgumentException("Informix routine ALTER history must not be blank");
		final var tokenizer = new InformixRoutineAlterSql(text);
		final List<Integer> starts = new ArrayList<>();
		int depth = 0;
		for (int i = 0; i < text.length();) {
			final var marker = tokenizer.getTextMarker(i);
			if (marker != null) {
				if (marker.getType() == Type.SINGLE_LINE_COMMENT && marker.getValue()
						.matches("(?is).*\\bALTER\\s+(?:(?:SPECIFIC\\s+)?(?:FUNCTION|PROCEDURE)|ROUTINE)\\b.*"))
					throw new IllegalArgumentException(
							"Ambiguous Informix ALTER history after a line comment; catalog text has no reliable statement separator");
				i = marker.getEnd();
				continue;
			}
			if (depth == 0 && text.regionMatches(true, i, "ALTER", 0, 5)
					&& (i == 0 || !Character.isJavaIdentifierPart(text.charAt(i - 1)))
					&& (i + 5 == text.length() || !Character.isJavaIdentifierPart(text.charAt(i + 5)))) {
				starts.add(i);
				i += 5;
				continue;
			}
			char ch = text.charAt(i++);
			if (starts.isEmpty() && !Character.isWhitespace(ch))
				throw new IllegalArgumentException("Expected Informix ALTER routine SQL");
			if (ch == '(')
				depth++;
			else if (ch == ')' && --depth < 0)
				throw new IllegalArgumentException("Unbalanced Informix ALTER routine SQL");
		}
		if (depth != 0 || starts.isEmpty())
			throw new IllegalArgumentException("Incomplete Informix ALTER routine SQL");
		final List<String> result = new ArrayList<>();
		for (int i = 0; i < starts.size(); i++) {
			String sql = text.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : text.length())
					.strip();
			if (!sql.matches("(?is)ALTER\\s+(?:(?:SPECIFIC\\s+)?(?:FUNCTION|PROCEDURE)|ROUTINE)\\b.*"))
				throw new IllegalArgumentException("Expected Informix ALTER FUNCTION/PROCEDURE/ROUTINE SQL");
			result.add(sql);
		}
		return result;
	}

	@Override
	protected int getQuotedTextEnd(String text, int start) {
		if (text.charAt(start) == '{') {
			int end = text.indexOf('}', start + 1);
			if (end < 0)
				throw new IllegalArgumentException("Unclosed Informix brace comment");
			return end + 1;
		}
		return super.getQuotedTextEnd(text, start);
	}
}
