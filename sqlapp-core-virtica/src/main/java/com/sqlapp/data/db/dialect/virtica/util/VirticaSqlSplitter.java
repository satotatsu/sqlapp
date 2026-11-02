/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class VirticaSqlSplitter extends SqlSplitter {

	public VirticaSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected int getQuotedTextEnd(String sql, int start) {
				if (sql.startsWith("$$", start) && (start == 0 || !identifier(sql.charAt(start - 1)))) {
					int end = sql.indexOf("$$", start + 2);
					if (end < 0)
						stringHolder.throwInvalidTextException("Closing dollar quote $$ not found");
					return end + 2;
				}
				return super.getQuotedTextEnd(sql, start);
			}

			private boolean identifier(char c) {
				return Character.isLetterOrDigit(c) || c == '_' || c == '$';
			}

			private boolean functionHeader(String sql, int offset) {
				int state = 0;
				for (int i = 0; i < sql.length();) {
					var marker = getTextMarker(offset + i);
					if (marker != null) {
						if (!marker.getType().isComment())
							return false;
						i = marker.getEnd() - offset;
						continue;
					}
					if (Character.isWhitespace(sql.charAt(i))) {
						i++;
						continue;
					}
					int start = i;
					while (i < sql.length() && identifier(sql.charAt(i)))
						i++;
					if (i == start)
						return false;
					String word = sql.substring(start, i);
					if (state == 0 && word.equalsIgnoreCase("CREATE"))
						state = 1;
					else if ((state == 1 || state == 3) && word.equalsIgnoreCase("FUNCTION"))
						return true;
					else if (state == 1 && word.equalsIgnoreCase("OR"))
						state = 2;
					else if (state == 2 && word.equalsIgnoreCase("REPLACE"))
						state = 3;
					else
						return false;
				}
				return false;
			}

			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder) {
				String sql = stringHolder.substringAt();
				int offset = stringHolder.getPosition();
				if (!functionHeader(sql, offset))
					return false;
				int depth = 0;
				for (int i = 0; i < sql.length();) {
					var marker = getTextMarker(offset + i);
					if (marker != null) {
						i = marker.getEnd() - offset;
						continue;
					}
					char c = sql.charAt(i);
					if (depth == 0 && sql.startsWith(getCurrentDelimiter(), i))
						return false;
					if (!Character.isLetter(c) && c != '_') {
						i++;
						continue;
					}
					int start = i++;
					while (i < sql.length() && identifier(sql.charAt(i)))
						i++;
					String word = sql.substring(start, i);
					if (word.equalsIgnoreCase("BEGIN") || depth > 0 && word.equalsIgnoreCase("CASE"))
						depth++;
					else if (depth > 0 && word.equalsIgnoreCase("END") && --depth == 0) {
						while (i < sql.length()) {
							var trailing = getTextMarker(offset + i);
							if (trailing != null && trailing.getType().isComment()) {
								i = trailing.getEnd() - offset;
								continue;
							}
							if (Character.isWhitespace(sql.charAt(i))) {
								i++;
								continue;
							}
							if (!sql.startsWith(getCurrentDelimiter(), i))
								stringHolder.throwInvalidTextException("Expected delimiter after function END");
							setPosition(offset + i);
							return true;
						}
						setPosition(-1);
						return true;
					}
				}
				if (depth > 0)
					stringHolder.throwInvalidTextException("Function END not found");
				return false;
			}
		};
	}
}
