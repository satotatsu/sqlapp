/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;
import com.sqlapp.util.CommonUtils;

public class OracleSqlSplitter extends SqlSplitter {

	public OracleSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	private static final Pattern CREATE_PATTERN = Pattern.compile("\\s*(CREATE|ALTER)\\s*.*",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	private static final Pattern BEGIN_PATTERN = Pattern.compile("\\s*(BEGIN|DECLARE)\\s*",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);
	private static final Pattern ANONYMOUS_PATTERN = Pattern.compile("\\s*(BEGIN|DECLARE)\\b.*",
			Pattern.CASE_INSENSITIVE + Pattern.DOTALL);
	private static final Pattern SLASH_LINE = Pattern.compile("(?m)^[\\t ]*/[\\t ]*\\r?$");

	@Override
	protected String getResult(SqlTokenizer tokenizer) {
		String result = super.getResult(tokenizer);
		if (result != null && ANONYMOUS_PATTERN.matcher(result).matches() && !result.endsWith(";"))
			return result + ";";
		return result;
	}

	private static final Pattern END_PATTERN = Pattern.compile("\\s*END\\s*(?<term>[^\\s;])?\\s*;\\s*/\\s*.*",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected int getQuotedTextEnd(String sql, int start) {
				if (start > 0 && Character.isJavaIdentifierPart(sql.charAt(start - 1)))
					return super.getQuotedTextEnd(sql, start);
				int prefix = sql.regionMatches(true, start, "nq'", 0, 3) ? 3
						: sql.regionMatches(true, start, "q'", 0, 2) ? 2 : 0;
				if (prefix == 0)
					return super.getQuotedTextEnd(sql, start);
				if (start + prefix >= sql.length())
					stringHolder.throwInvalidTextException("Alternative quote delimiter not found");
				char open = sql.charAt(start + prefix);
				if (Character.isWhitespace(open) || open == '\'')
					stringHolder.throwInvalidTextException("Invalid alternative quote delimiter");
				char close = switch (open) {
				case '[' -> ']';
				case '(' -> ')';
				case '{' -> '}';
				case '<' -> '>';
				default -> open;
				};
				int end = sql.indexOf("" + close + "'", start + prefix + 1);
				if (end < 0)
					stringHolder.throwInvalidTextException("Closing alternative quote [" + close + "'] not found");
				return end + 2;
			}

			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder) {
				if (ANONYMOUS_PATTERN.matcher(text).matches()) {
					String remainder = stringHolder.substringAt();
					Matcher slash = SLASH_LINE.matcher(remainder);
					while (slash.find()) {
						int position = stringHolder.getPosition() + slash.start() + slash.group().indexOf('/');
						if (getTextMarker(position) == null) {
							setPosition(position);
							return true;
						}
					}
					stringHolder.throwInvalidTextException("Delimiter[/] not found for anonymous PL/SQL block");
				}
				Matcher matcher = CREATE_PATTERN.matcher(text);
				if (matcher.matches()) {
					int delPos = stringHolder.indexOf(this.getCurrentDelimiter(), stringHolder.getPosition() + 10);
					int beginPos = stringHolder.searchLineOf(BEGIN_PATTERN, stringHolder.getPosition() + 10);
					if (beginPos < 0) {
						return false;
					}
					if (delPos < beginPos) {
						return false;
					}
					int ePos = searchEnd(stringHolder, beginPos + 6);
					if (ePos >= 0) {
						int del = stringHolder.indexOf("/", ePos + 4);
						setPosition(del);
						return true;
					} else {
						stringHolder.throwInvalidTextException(
								"Delimiter[/] not found.[" + stringHolder.substringAt() + "]");
					}
				}
				return false;
			}

			private int searchEnd(StringHolder stringHolder, int pos) {
				int ePos = stringHolder.searchLineOf(END_PATTERN, pos + 6, false, (i, matcher) -> {
					String term = matcher.group("term");
					if (CommonUtils.isEmpty(term)) {
						return true;
					}
					if ("IF".equalsIgnoreCase(term)) {
						return false;
					}
					if ("LOOP".equalsIgnoreCase(term)) {
						return false;
					}
					if ("CASE".equalsIgnoreCase(term)) {
						return false;
					}
					return true;
				});
				return ePos;
			}

		};

	}
}
