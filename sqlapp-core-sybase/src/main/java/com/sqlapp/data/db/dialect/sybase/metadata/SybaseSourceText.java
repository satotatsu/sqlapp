/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import java.sql.SQLException;
import java.util.regex.Pattern;
import com.sqlapp.jdbc.ExResultSet;

/** Lossless source handling for ASE's chunked syscomments catalog. */
public final class SybaseSourceText {
	private SybaseSourceText() {
	}

	static String required(ExResultSet rs, String field, String object) throws SQLException {
		if (rs.getInt("source_group") > 1)
			throw new UnsupportedOperationException(
					"Numbered SAP ASE procedure groups require separate extraction: " + object);
		if (rs.getInt("source_hidden") != 0)
			throw new IllegalStateException("SAP ASE source is hidden: " + object);
		final String text = rs.getString(field);
		if (text == null)
			throw new IllegalStateException("Missing SAP ASE source text: " + object);
		return text;
	}

	/** Masks comments, literals and quoted identifiers without shifting offsets. */
	public static String mask(String text) {
		final var result = new StringBuilder(text);
		for (int i = 0; i < text.length();) {
			final char c = text.charAt(i);
			int end = i + 1;
			if (c == '\'' || c == '"' || c == '[') {
				final char close = c == '[' ? ']' : c;
				while (end < text.length()) {
					if (text.charAt(end++) == close) {
						if (end < text.length() && text.charAt(end) == close)
							end++;
						else
							break;
					}
				}
			} else if (c == '-' && end < text.length() && text.charAt(end) == '-') {
				while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r')
					end++;
			} else if (c == '/' && end < text.length() && text.charAt(end) == '*') {
				end++;
				int depth = 1;
				while (end < text.length() && depth > 0) {
					if (end + 1 < text.length() && text.startsWith("/*", end)) {
						depth++;
						end += 2;
					} else if (end + 1 < text.length() && text.startsWith("*/", end)) {
						depth--;
						end += 2;
					} else
						end++;
				}
			} else {
				i++;
				continue;
			}
			for (int j = i; j < end; j++)
				if (result.charAt(j) != '\n' && result.charAt(j) != '\r')
					result.setCharAt(j, ' ');
			i = end;
		}
		return result.toString();
	}

	static String defaultExpression(String source) {
		final String masked = mask(source);
		final var create = Pattern.compile("(?is)^\\s*CREATE(?:\\s+OR\\s+REPLACE)?\\s+DEFAULT\\b").matcher(masked);
		int start = 0;
		if (create.find()) {
			final var as = Pattern.compile("(?i)\\bAS\\b").matcher(masked);
			if (!as.find(create.end()))
				throw new IllegalStateException("Missing AS in SAP ASE CREATE DEFAULT");
			start = as.end();
		} else {
			final var inline = Pattern.compile("(?is)^\\s*DEFAULT\\b").matcher(masked);
			if (inline.find())
				start = inline.end();
		}
		final String expression = source.substring(start).strip();
		if (expression.isEmpty())
			throw new IllegalStateException("Empty SAP ASE default expression");
		return expression;
	}

	static String viewStatement(String source) {
		final String masked = mask(source);
		final var create = Pattern.compile("(?is)^\\s*CREATE(?:\\s+OR\\s+REPLACE)?\\s+VIEW\\b").matcher(masked);
		final var as = Pattern.compile("(?i)\\bAS\\b").matcher(masked);
		if (!create.find() || !as.find(create.end()))
			throw new IllegalStateException("Missing SAP ASE CREATE VIEW AS header");
		return source.substring(as.end()).stripLeading();
	}
}
