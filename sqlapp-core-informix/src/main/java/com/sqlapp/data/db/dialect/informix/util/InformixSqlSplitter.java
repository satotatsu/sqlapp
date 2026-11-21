/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;

/**
 * Keeps SPL bodies intact while splitting ordinary statements at semicolons.
 */
public class InformixSqlSplitter extends SqlSplitter {
	private static final Pattern ROUTINE = Pattern
			.compile("^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(PROCEDURE|FUNCTION)\\b", Pattern.CASE_INSENSITIVE);

	public InformixSqlSplitter(final Dialect dialect) {
		super(dialect);
	}

	@Override
	public List<SplitResult> parse(final String input) {
		statements = new ArrayList<>();
		final var code = new StringBuilder();
		int start = 0;
		for (int i = 0; i < input.length();) {
			final char ch = input.charAt(i);
			int end = i;
			if (input.startsWith("--", i)) {
				end = input.indexOf('\n', i + 2);
				if (end < 0)
					end = input.length();
			} else if (input.startsWith("/*", i) || ch == '{') {
				final String close = ch == '{' ? "}" : "*/";
				end = input.indexOf(close, i + (ch == '{' ? 1 : 2));
				if (end < 0)
					throw new IllegalArgumentException("Unterminated Informix comment at offset " + i);
				end += close.length();
			} else if (ch == '\'' || ch == '"') {
				end = quotedEnd(input, i, ch);
			}
			if (end > i) {
				// A quoted token is SQL content, even if it contains no unquoted words.
				if (ch == '\'' || ch == '"')
					code.append('?').append(" ".repeat(end - i - 1));
				else
					code.append(" ".repeat(end - i));
				i = end;
				continue;
			}
			if (ch == ';' && complete(code.toString())) {
				add(input.substring(start, i), code.toString());
				start = ++i;
				code.setLength(0);
			} else {
				code.append(ch);
				i++;
			}
		}
		if (!complete(code.toString()))
			throw new IllegalArgumentException(
					"Incomplete Informix SPL routine: expected END PROCEDURE or END FUNCTION");
		add(input.substring(start), code.toString());
		return statements;
	}

	private static int quotedEnd(final String input, final int start, final char quote) {
		for (int i = start + 1; i < input.length(); i++) {
			if (input.charAt(i) == quote) {
				if (i + 1 < input.length() && input.charAt(i + 1) == quote)
					i++;
				else
					return i + 1;
			}
		}
		throw new IllegalArgumentException("Unterminated Informix quoted text at offset " + start);
	}

	private static boolean complete(final String code) {
		final var routine = ROUTINE.matcher(code);
		if (!routine.find())
			return true;
		// External routines need no SPL terminator. DOCUMENT/WITH LISTING may follow
		// END.
		return Pattern.compile("\\bEND\\s+" + routine.group(1) + "\\b|\\bEXTERNAL\\s+NAME\\b", Pattern.CASE_INSENSITIVE)
				.matcher(code.substring(routine.end())).find();
	}

	private void add(final String sql, final String code) {
		if (!sql.isBlank())
			statements.add(new SplitResult(code.isBlank() ? TextType.COMMENT : TextType.SQL, sql.strip()));
	}
}
