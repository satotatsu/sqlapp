/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;

public class PostgresSqlSplitter extends SqlSplitter {

	public PostgresSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	private boolean standardConformingStrings = true;

	/**
	 * Defaults to modern PostgreSQL quoting; specify false for a legacy/non-default
	 * session.
	 */
	public boolean isStandardConformingStrings() {
		return standardConformingStrings;
	}

	public void setStandardConformingStrings(boolean standardConformingStrings) {
		this.standardConformingStrings = standardConformingStrings;
	}

	private static final Pattern QUOTING_CHANGE = Pattern.compile(
			"(?is)^\\s*(?:SET\\s+(?:(?:SESSION|LOCAL)\\s+)?(?:\"standard_conforming_strings\"|standard_conforming_strings)\\s*(?:=|TO\\b)"
					+ "|RESET\\s+(?:\"standard_conforming_strings\"|standard_conforming_strings|ALL)(?:\\s|$)"
					+ "|DISCARD\\s+ALL(?:\\s|$)).*");
	private static final Pattern SET_CONFIG = Pattern
			.compile("(?i)(?:(?<![\\p{L}\\p{N}_$])set_config|\"set_config\")\\s*\\(");

	private static final Pattern DOLLAR_TAG = Pattern.compile("\\$(?:[\\p{L}_][\\p{L}\\p{N}_]*)?\\$");

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected boolean supportsNestedBlockComments() {
				return true;
			}

			@Override
			protected int getQuotedTextEnd(String sql, int start) {
				char c = sql.charAt(start);
				if (c == '\'' && (!standardConformingStrings
						|| start > 0 && (sql.charAt(start - 1) == 'E' || sql.charAt(start - 1) == 'e')
								&& (start < 2 || !identifier(sql.charAt(start - 2)))))
					return findQuotedTextEnd(sql, start, c, true);
				if (c == '$' && (start == 0 || !identifier(sql.charAt(start - 1)))) {
					Matcher tag = DOLLAR_TAG.matcher(sql).region(start, sql.length());
					if (tag.lookingAt()) {
						int end = sql.indexOf(tag.group(), tag.end());
						if (end < 0)
							stringHolder
									.throwInvalidTextException("Closing dollar quote " + tag.group() + " not found");
						return end + tag.group().length();
					}
				}
				return super.getQuotedTextEnd(sql, start);
			}

			@Override
			protected void onStatement(String statement) {
				String code = maskStringLiterals(statement, true, false);
				boolean change = QUOTING_CHANGE.matcher(code).matches();
				Matcher call = SET_CONFIG.matcher(code);
				while (!change && call.find()) {
					String args = statement.substring(call.end()).stripLeading();
					if (!args.startsWith("'")) {
						change = true;
					} else {
						int end = findQuotedTextEnd(args, 0, '\'', !standardConformingStrings);
						String name = args.substring(1, end - 1);
						change = name.indexOf('\\') >= 0 || name.contains("''")
								|| name.equalsIgnoreCase("standard_conforming_strings")
								|| !args.substring(end).stripLeading().startsWith(",");
					}
				}
				if (change)
					stringHolder.throwInvalidTextException(
							"Offline SQL splitting requires fixed standard_conforming_strings. "
									+ "Split the script at quoting changes and configure setStandardConformingStrings "
									+ "for each segment; session, transaction and dynamic setting expressions are not evaluated.");
			}

			private boolean identifier(char c) {
				return Character.isLetterOrDigit(c) || c == '_' || c == '$';
			}
		};
	}
}
