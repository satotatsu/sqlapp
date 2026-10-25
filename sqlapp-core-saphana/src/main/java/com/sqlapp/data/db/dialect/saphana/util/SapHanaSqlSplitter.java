/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class SapHanaSqlSplitter extends SqlSplitter {

	public SapHanaSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	private static final Pattern ANONYMOUS_PATTERN = Pattern.compile("\\s*DO\\b.*",
			Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	private static final Pattern CREATE_PATTERN = Pattern.compile("\\s*(CREATE|ALTER)\\s*.*",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	private static final Pattern BEGIN_PATTERN = Pattern.compile("\\s*BEGIN\\s*",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	private static final Pattern END_PATTERN = Pattern.compile("\\s*END\\s*;?",
			Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.DOTALL);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder) {
				if (stringHolder.substringMatcher(ANONYMOUS_PATTERN).matches()) {
					setPosition(anonymousBlockEnd(stringHolder));
					return true;
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
					int i = beginPos;
					while (true) {
						int ePos = stringHolder.searchLineOf(END_PATTERN, i + 6);
						if (ePos < 0) {
							stringHolder.throwInvalidTextException(
									"[END] element not found.[" + stringHolder.substringAt() + "]");
						}
						int bPos = stringHolder.searchLineOf(BEGIN_PATTERN, i + 6);
						if (bPos >= 0) {
							if (bPos < ePos) {
								i = ePos + 3;
								continue;
							} else {
								int last = stringHolder.indexOf(this.getCurrentDelimiter(), ePos + 3);
								setPosition(last);
								return true;
							}
						} else {
							int last = stringHolder.indexOf(this.getCurrentDelimiter(), ePos + 3);
							setPosition(last);
							return true;
						}
					}
				}
				return false;
			}

			private int anonymousBlockEnd(StringHolder holder) {
				var blocks = new java.util.ArrayDeque<String>();
				boolean started = false;
				boolean statementStart = false;
				String suffix = null;
				for (int i = holder.getPosition(); i < holder.getContextLength();) {
					TextMarker marker = getTextMarker(i);
					if (marker != null) {
						if (started && blocks.isEmpty() && !marker.getType().isComment())
							holder.throwInvalidTextException("Expected delimiter after anonymous DO block END");
						if (!marker.getType().isComment())
							suffix = null;
						i = marker.getEnd();
						continue;
					}
					char c = holder.charAt(i);
					if (Character.isWhitespace(c)) {
						i++;
						continue;
					}
					if (started && blocks.isEmpty()) {
						if (c == ';')
							return i;
						holder.throwInvalidTextException("Expected delimiter after anonymous DO block END");
					}
					if (c == ';') {
						suffix = null;
						if (!started)
							holder.throwInvalidTextException("BEGIN not found in anonymous DO block");
						statementStart = true;
						i++;
						continue;
					}
					if (!Character.isJavaIdentifierStart(c)) {
						suffix = null;
						if (c == ':')
							statementStart = true;
						i++;
						continue;
					}
					StringBuilder word = new StringBuilder();
					while (i < holder.getContextLength() && Character.isJavaIdentifierPart(holder.charAt(i)))
						word.append(holder.charAt(i++));
					String token = word.toString().toUpperCase(java.util.Locale.ROOT);
					if (!started) {
						if (token.equals("BEGIN")) {
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
					if (token.equals("END")) {
						suffix = blocks.pop();
						statementStart = false;
					} else if (token.equals("BEGIN") || token.equals("CASE")
							|| statementStart && java.util.Set.of("IF", "WHILE", "FOR", "LOOP").contains(token)) {
						blocks.push(token);
						statementStart = token.equals("BEGIN") || token.equals("LOOP");
					} else {
						statementStart = java.util.Set.of("THEN", "ELSE", "DO").contains(token);
					}
				}
				if (!started || !blocks.isEmpty())
					holder.throwInvalidTextException("Unclosed anonymous DO block; expected BEGIN ... END");
				return -1;
			}

		};
	}
}
