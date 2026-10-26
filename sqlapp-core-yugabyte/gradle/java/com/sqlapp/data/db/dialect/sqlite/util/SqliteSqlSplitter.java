/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.util;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class SqliteSqlSplitter extends SqlSplitter {

	public SqliteSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqliteTokenizer(input);
	}

	/** Keeps all SQLite quoting forms and comments opaque to delimiter matching. */
	private static final class SqliteTokenizer extends SqlTokenizer {
		private final String sql;
		private int position;
		private String current;
		private boolean comment;

		private SqliteTokenizer(String input) {
			super(input);
			sql = stringHolder.substringAt();
		}

		@Override
		protected void preParse(StringHolder holder) {
			// Lex once, in order: comment markers inside literals are ordinary text.
		}

		@Override
		public boolean hasNext() {
			while (position < sql.length() && (Character.isWhitespace(sql.charAt(position))
					|| sql.charAt(position) == ';' || position == 0 && sql.charAt(position) == '\ufeff'))
				position++;
			if (position == sql.length())
				return false;
			int start = position;
			comment = sql.startsWith("--", position) || sql.startsWith("/*", position);
			if (comment) {
				position = tokenEnd(position);
				current = sql.substring(start, position);
				return true;
			}
			List<String> header = new ArrayList<>();
			String previous = "";
			boolean trigger = false;
			boolean triggerEnded = false;
			while (position < sql.length()) {
				if (Character.isWhitespace(sql.charAt(position))) {
					position++;
					continue;
				}
				boolean internalComment = sql.startsWith("--", position) || sql.startsWith("/*", position);
				int end = tokenEnd(position);
				String token = sql.substring(position, end);
				position = end;
				if (internalComment)
					continue;
				if (header.size() < 6)
					header.add(token.toUpperCase(java.util.Locale.ROOT));
				trigger |= isTrigger(header);
				if (triggerEnded && !token.equals(";"))
					fail("Expected a delimiter after CREATE TRIGGER END");
				if (token.equals(";") && (!trigger || triggerEnded))
					break;
				// Every trigger step ends in ';'. A CASE expression's END is part
				// of its step and cannot follow that step's terminating semicolon.
				if (trigger && token.equalsIgnoreCase("END") && previous.equals(";"))
					triggerEnded = true;
				previous = token;
			}
			if (trigger && !triggerEnded)
				fail("Unterminated CREATE TRIGGER: expected ; END");
			current = sql.substring(start, position);
			return true;
		}

		private boolean isTrigger(List<String> header) {
			int create = !header.isEmpty() && header.get(0).equals("EXPLAIN") ? 1 : 0;
			if (create == 1 && header.size() > 2 && header.get(1).equals("QUERY") && header.get(2).equals("PLAN"))
				create = 3;
			if (header.size() <= create || !header.get(create).equals("CREATE"))
				return false;
			int kind = create + 1;
			if (header.size() > kind && (header.get(kind).equals("TEMP") || header.get(kind).equals("TEMPORARY")))
				kind++;
			return header.size() > kind && header.get(kind).equals("TRIGGER");
		}

		private int tokenEnd(int start) {
			if (sql.startsWith("--", start)) {
				int end = sql.indexOf('\n', start + 2);
				return end < 0 ? sql.length() : end + 1;
			}
			if (sql.startsWith("/*", start)) {
				int end = sql.indexOf("*/", start + 2);
				if (end < 0)
					fail("Unterminated block comment");
				return end + 2;
			}
			char first = sql.charAt(start);
			if (first == '\'' || first == '"' || first == '`' || first == '[') {
				char quote = first == '[' ? ']' : first;
				for (int end = start + 1; end < sql.length(); end++) {
					if (sql.charAt(end) != quote)
						continue;
					if (quote != ']' && end + 1 < sql.length() && sql.charAt(end + 1) == quote) {
						end++;
						continue;
					}
					return end + 1;
				}
				fail("Unterminated quoted token");
			}
			int end = start + 1;
			if (Character.isLetterOrDigit(first) || first == '_')
				while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_'
						|| sql.charAt(end) == '$'))
					end++;
			return end;
		}

		private void fail(String message) {
			int at = Math.min(position, sql.length());
			int line = 1, start = 0;
			for (int i = 0; i < at; i++) {
				if (sql.charAt(i) == '\n') {
					line++;
					start = i + 1;
				}
			}
			int end = sql.indexOf('\n', at);
			throw new com.sqlapp.exceptions.InvalidTextException(sql.substring(start, end < 0 ? sql.length() : end),
					line, at - start + 1, message);
		}

		@Override
		public String next() {
			return current;
		}

		@Override
		public boolean isComment() {
			return comment;
		}
	}
}
