/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;

/**
 * Splits DuckDB scripts. Besides the standard quoting rules it keeps
 * {@code $$...$$} / {@code $tag$...$tag$} strings, backslash escapes in
 * {@code E'...'} strings and nested block comments opaque to {@code ;}.
 */
public class DuckDbSqlSplitter extends SqlSplitter {

	private static final Pattern DOLLAR_TAG = Pattern.compile("\\$(?:[\\p{L}_][\\p{L}\\p{N}_]*)?\\$");

	public DuckDbSqlSplitter(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlTokenizer createSqlTokenizer(final String input) {
		return new SqlTokenizer(input) {
			@Override
			protected boolean supportsNestedBlockComments() {
				return true;
			}

			@Override
			protected int getQuotedTextEnd(final String sql, final int start) {
				final char c = sql.charAt(start);
				if (c == '\'' && start > 0 && (sql.charAt(start - 1) == 'E' || sql.charAt(start - 1) == 'e')
						&& (start < 2 || !identifier(sql.charAt(start - 2)))) {
					return findQuotedTextEnd(sql, start, c, true);
				}
				if (c == '$' && (start == 0 || !identifier(sql.charAt(start - 1)))) {
					final Matcher tag = DOLLAR_TAG.matcher(sql).region(start, sql.length());
					if (tag.lookingAt()) {
						final int end = sql.indexOf(tag.group(), tag.end());
						if (end < 0) {
							stringHolder
									.throwInvalidTextException("Closing dollar quote " + tag.group() + " not found");
						}
						return end + tag.group().length();
					}
				}
				return super.getQuotedTextEnd(sql, start);
			}

			private boolean identifier(final char c) {
				return Character.isLetterOrDigit(c) || c == '_' || c == '$';
			}
		};
	}
}
