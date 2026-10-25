/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class MySqlSqlSplitter extends SqlSplitter {

	public MySqlSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	private boolean noBackslashEscapes;
	private boolean ansiQuotes;

	/** Initial session policy. No server state or SET expressions are evaluated. */
	public boolean isNoBackslashEscapes() {
		return noBackslashEscapes;
	}

	public void setNoBackslashEscapes(boolean noBackslashEscapes) {
		this.noBackslashEscapes = noBackslashEscapes;
	}

	public boolean isAnsiQuotes() {
		return ansiQuotes;
	}

	public void setAnsiQuotes(boolean ansiQuotes) {
		this.ansiQuotes = ansiQuotes;
	}

	private static final Pattern SQL_MODE_ASSIGNMENT = Pattern
			.compile("(?is)(?:^\\s*SET\\s+(?:(?:SESSION|LOCAL|GLOBAL|PERSIST|PERSIST_ONLY)\\s+)?|,\\s*)"
					+ "(?:@@\\s*(?:(?:SESSION|LOCAL|GLOBAL)\\s*\\.\\s*)?)?"
					+ "(?:`sql_mode`|\"sql_mode\"|sql_mode)\\s*(?::=|=)");

	private static final Pattern CHANGE_DELIMITER = Pattern.compile("\\s*delimiter\\s+(?<delimiter>[^\\s]+)\\s*",
			Pattern.CASE_INSENSITIVE);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected int getQuotedTextEnd(String sql, int start) {
				char quote = sql.charAt(start);
				if (quote == '\'' || quote == '"' || quote == '`')
					return findQuotedTextEnd(sql, start, quote,
							quote != '`' && !noBackslashEscapes && !(quote == '"' && ansiQuotes));
				return -1;
			}

			@Override
			protected void onStatement(String statement) {
				String code = maskStringLiterals(statement, ansiQuotes, true);
				if (code.stripLeading().matches("(?is)SET\\b.*") && SQL_MODE_ASSIGNMENT.matcher(code).find())
					stringHolder.throwInvalidTextException("Offline SQL splitting requires a fixed sql_mode. "
							+ "Split the script at sql_mode assignments and configure setNoBackslashEscapes "
							+ "and setAnsiQuotes for each segment; SET expressions are not evaluated.");
			}

			@Override
			protected void onComment(String comment) {
				int prefix = comment.startsWith("/*!") ? 3 : comment.startsWith("/*M!") ? 4 : 0;
				if (prefix != 0) {
					String body = comment.substring(prefix, comment.length() - 2).replaceFirst("^\\d+", "");
					onStatement(body);
				}
			}

			@Override
			protected boolean isLineCommentStart(String sql, int position) {
				return sql.charAt(position) == '#' || sql.startsWith("--", position)
						&& (position + 2 == sql.length() || Character.isWhitespace(sql.charAt(position + 2)));
			}

			@Override
			protected boolean isChangeDelimiter(String text, StringHolder stringHolder) {
				Matcher matcher = CHANGE_DELIMITER.matcher(text);
				if (matcher.matches()) {
					String delimiter = matcher.group("delimiter");
					this.setCurrentDelimiter(delimiter);
					stringHolder.addPosition(matcher.group(0).length());
					return true;
				}
				return false;
			}

			@Override
			protected boolean isLineComment(String value) {
				String text = value.stripLeading();
				return !text.isEmpty() && isLineCommentStart(text, 0);
			}

		};
	}

	@Override
	protected TextType getTextType(boolean isComment, String sql) {
		if (isComment) {
			Matcher matcher = DIRECTIVE_PATTERN.matcher(sql);
			if (matcher.matches()) {
				return TextType.COMMENT_DIRECTIVE;
			}
			return TextType.COMMENT;
		}
		return TextType.SQL;
	}

	private static final Pattern DIRECTIVE_PATTERN = Pattern.compile("/\\*![0-9]{5}\\s+.*?\\*/",
			Pattern.MULTILINE + Pattern.DOTALL);

}
