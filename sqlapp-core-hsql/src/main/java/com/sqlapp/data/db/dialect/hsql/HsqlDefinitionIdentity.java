/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Comparison only: generated or user-authored SQL is never rewritten. */
final class HsqlDefinitionIdentity {
	private HsqlDefinitionIdentity() {
	}

	private static final Pattern NUMBER = Pattern.compile("(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

	static boolean equivalent(String left, String right) {
		return tokens(left).equals(tokens(right));
	}

	private static List<String> tokens(String sql) {
		var result = new ArrayList<String>();
		for (int i = 0; i < sql.length();) {
			char ch = sql.charAt(i);
			if (Character.isWhitespace(ch)) {
				i++;
				continue;
			}
			if (sql.startsWith("--", i)) {
				while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r')
					i++;
				continue;
			}
			if (sql.startsWith("/*", i)) {
				int depth = 1;
				i += 2;
				while (i < sql.length() && depth > 0) {
					if (sql.startsWith("/*", i)) {
						depth++;
						i += 2;
					} else if (sql.startsWith("*/", i)) {
						depth--;
						i += 2;
					} else
						i++;
				}
				if (depth != 0)
					throw new IllegalArgumentException("Unterminated definition comment");
				continue;
			}
			if (ch == '\'' || ch == '"') {
				int start = i++;
				boolean closed = false;
				while (i < sql.length()) {
					if (sql.charAt(i++) != ch)
						continue;
					if (i < sql.length() && sql.charAt(i) == ch) {
						i++;
						continue;
					}
					closed = true;
					break;
				}
				if (!closed)
					throw new IllegalArgumentException("Unterminated definition quote");
				result.add(sql.substring(start, i));
				continue;
			}
			var number = NUMBER.matcher(sql).region(i, sql.length());
			if (number.lookingAt()) {
				result.add(number.group());
				i = number.end();
				continue;
			}
			if (Character.isJavaIdentifierStart(ch)) {
				int start = i++;
				while (i < sql.length() && Character.isJavaIdentifierPart(sql.charAt(i)))
					i++;
				result.add(sql.substring(start, i).toUpperCase(Locale.ROOT));
			} else
				result.add(String.valueOf(sql.charAt(i++)));
		}
		for (int i = 0; i + 1 < result.size(); i++)
			if (result.get(i).equals("DEFAULT")) {
				int value = i + 1;
				String sign = "";
				if (result.get(value).equals("+") || result.get(value).equals("-")) {
					sign = result.get(value++);
				}
				if (value < result.size() && NUMBER.matcher(result.get(value)).matches() && (value + 1 == result.size()
						|| result.get(value + 1).equals("CHECK") || result.get(value + 1).equals("CONSTRAINT"))) {
					result.set(i + 1, new BigDecimal(sign + result.get(value)).stripTrailingZeros().toPlainString());
					if (value != i + 1)
						result.remove(value);
				}
			}
		return result;
	}
}
