/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.util;

import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class HsqlSqlSplitter extends SqlSplitter {

	public HsqlSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	private static final Pattern FUNCTION_PATTERN = Pattern.compile(
			"\\s*(CREATE|ALTER)\\s+(AGGREGATE\\s+)?(FUNCTION|PROCEDURE|SPECIFIC\\s+ROUTINE)\\b.*",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder) {
				if (stringHolder.substringMatcher(FUNCTION_PATTERN).matches()) {
					int delimiter = routineDelimiter(stringHolder, getCurrentDelimiter());
					if (delimiter >= 0) {
						setPosition(delimiter);
						return true;
					}
				}
				return false;
			}
		};
	}

	/**
	 * Finds a compound routine boundary without interpreting or rewriting its SQL.
	 */
	private static int routineDelimiter(StringHolder input, String delimiter) {
		var blocks = new java.util.ArrayDeque<String>();
		boolean started = false, complete = false;
		boolean statementStart = false;
		String suffix = null;
		for (int i = input.getPosition(); i < input.getContextLength();) {
			char c = input.charAt(i);
			if (starts(input, i, "--")) {
				while (i < input.getContextLength() && input.charAt(i) != '\n')
					i++;
				continue;
			}
			if (starts(input, i, "/*")) {
				int depth = 1;
				i += 2;
				while (i < input.getContextLength() && depth > 0) {
					if (starts(input, i, "/*")) {
						depth++;
						i += 2;
					} else if (starts(input, i, "*/")) {
						depth--;
						i += 2;
					} else
						i++;
				}
				if (depth != 0)
					input.throwInvalidTextException("Unclosed HSQLDB routine comment");
				continue;
			}
			if (c == '\'' || c == '"') {
				char quote = c;
				boolean closed = false;
				i++;
				while (i < input.getContextLength()) {
					if (input.charAt(i++) == quote) {
						if (i < input.getContextLength() && input.charAt(i) == quote)
							i++;
						else {
							closed = true;
							break;
						}
					}
				}
				if (!closed)
					input.throwInvalidTextException("Unclosed HSQLDB routine quote");
				continue;
			}
			if (starts(input, i, delimiter)) {
				if (!started)
					return -1;
				if (complete)
					return i;
				statementStart = true;
				i += delimiter.length();
				continue;
			}
			if (Character.isJavaIdentifierStart(c)) {
				var word = new StringBuilder();
				while (i < input.getContextLength() && Character.isJavaIdentifierPart(input.charAt(i)))
					word.append(input.charAt(i++));
				String token = word.toString().toUpperCase(java.util.Locale.ROOT);
				if (complete)
					continue;
				if (!started) {
					if ("BEGIN".equals(token)) {
						started = true;
						statementStart = true;
						blocks.push(token);
					}
					continue;
				}
				if (token.equals(suffix)) {
					suffix = null;
					statementStart = false;
					continue;
				}
				suffix = null;
				if ("END".equals(token)) {
					suffix = blocks.pop();
					complete = blocks.isEmpty();
					statementStart = false;
				} else if ("BEGIN".equals(token) || "CASE".equals(token)
						|| statementStart && java.util.Set.of("IF", "WHILE", "FOR", "LOOP", "REPEAT").contains(token)) {
					blocks.push(token);
					statementStart = java.util.Set.of("BEGIN", "LOOP", "REPEAT").contains(token);
				} else if (java.util.Set.of("THEN", "DO", "ELSE").contains(token)) {
					statementStart = true;
				} else if (!"ATOMIC".equals(token)) {
					statementStart = false;
				}
			} else {
				if (c == ':')
					statementStart = true;
				i++;
			}
		}
		if (started && !complete)
			input.throwInvalidTextException("Unclosed HSQLDB routine block");
		return complete ? input.getContextLength() : -1;
	}

	private static boolean starts(StringHolder input, int at, String value) {
		if (at + value.length() > input.getContextLength())
			return false;
		for (int j = 0; j < value.length(); j++)
			if (input.charAt(at + j) != value.charAt(j))
				return false;
		return true;
	}
}
