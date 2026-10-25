/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.util;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

import lombok.Data;

public class SqlTokenizer {

	private String delimiter = ";";

	private String currentDelimiter = null;

	protected StringHolder stringHolder;

	private boolean comment = false;

	public SqlTokenizer(String original) {
		this.stringHolder = new StringHolder(original);
		preParse(stringHolder);
		currentDelimiter = null;
	}

	private TreeMap<Integer, TextMarker> textMarkers = new TreeMap<>();

	protected void preParse(StringHolder holder) {
		String sql = holder.substringAt();
		StringHolder cursor = holder.clone();
		StringBuilder statement = new StringBuilder();
		for (int i = 0; i < sql.length();) {
			boolean lineComment = false;
			// Client delimiter directives must be recognized before line comments.
			if (i == 0 || sql.charAt(i - 1) == '\n') {
				cursor.setPosition(i);
				int lineEnd = sql.indexOf('\n', i);
				String line = sql.substring(i, lineEnd < 0 ? sql.length() : lineEnd);
				if (isChangeDelimiter(line, cursor)) {
					i = cursor.getPosition();
					continue;
				}
				// Preserve the existing whole-line comment extension point.
				lineComment = isLineComment(line);
			}
			if (sql.startsWith(getCurrentDelimiter(), i)) {
				onStatement(statement.toString());
				statement.setLength(0);
				i += getCurrentDelimiter().length();
				continue;
			}
			int start = i;
			Type type = Type.QUOTED_STRING;
			if (lineComment || isLineCommentStart(sql, i)) {
				i = sql.indexOf('\n', i);
				if (i < 0)
					i = sql.length();
				type = Type.SINGLE_LINE_COMMENT;
			} else if (sql.startsWith("/*", i)) {
				int depth = 1;
				i += 2;
				while (i < sql.length() && depth > 0) {
					if (supportsNestedBlockComments() && sql.startsWith("/*", i)) {
						depth++;
						i += 2;
					} else if (sql.startsWith("*/", i)) {
						depth--;
						i += 2;
					} else {
						i++;
					}
				}
				if (depth != 0)
					holder.throwInvalidTextException("Closing block comment [*/] not found");
				type = Type.MULTI_LINE_COMMENT;
			} else {
				int end = getQuotedTextEnd(sql, i);
				if (end < 0) {
					statement.append(sql.charAt(i));
					i++;
					continue;
				}
				i = end;
			}
			if (type.isComment()) {
				onComment(sql.substring(start, i));
				statement.append(' ');
			} else {
				statement.append(sql, start, i);
			}
			TextMarker marker = new TextMarker();
			marker.setStart(start);
			marker.setEnd(i);
			marker.setValue(sql.substring(start, i));
			marker.setType(type);
			addTextMarker(marker);
		}
		onStatement(statement.toString());
	}

	/** Validates a lexical statement with comments replaced by spaces, before scanning the next one. */
	protected void onStatement(String statement) {
	}

	/** Dialects may validate executable comments; ordinary comments have no effect by default. */
	protected void onComment(String comment) {
	}

	/** Masks literals and complex quoted identifiers for offline statement checks. */
	protected String maskStringLiterals(String statement, boolean doubleQuotedIdentifiers, boolean backtickIdentifiers) {
		StringBuilder code = new StringBuilder(statement);
		for (int i = 0; i < statement.length();) {
			int end = getQuotedTextEnd(statement, i);
			if (end < 0) {
				i++;
				continue;
			}
			char quote = statement.charAt(i);
			boolean identifier = (doubleQuotedIdentifiers && quote == '"' || backtickIdentifiers && quote == '`')
					&& statement.substring(i + 1, end - 1).matches("[A-Za-z_][A-Za-z_0-9]*");
			if (!identifier) {
				for (int j = i; j < end; j++)
					code.setCharAt(j, ' ');
			}
			i = end;
		}
		return code.toString();
	}

	/** Dialects can add quoted forms; the result is exclusive, or -1 for ordinary SQL. */
	protected int getQuotedTextEnd(String sql, int start) {
		char quote = sql.charAt(start);
		if (quote == '\'' || quote == '"')
			return findQuotedTextEnd(sql, start, quote, false);
		return -1;
	}

	/** Finds a doubled-quote token, with optional backslash escaping. */
	protected int findQuotedTextEnd(String sql, int start, char quote, boolean backslashEscapes) {
		for (int i = start + 1; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (backslashEscapes && c == '\\') {
				i++;
			} else if (c == quote) {
				if (i + 1 < sql.length() && sql.charAt(i + 1) == quote)
					i++;
				else
					return i + 1;
			}
		}
		stringHolder.throwInvalidTextException("Closing quote [" + quote + "] not found");
		return -1;
	}

	protected boolean isLineCommentStart(String sql, int position) {
		if (!sql.startsWith("--", position))
			return false;
		int end = sql.indexOf('\n', position);
		return isLineComment(sql.substring(position, end < 0 ? sql.length() : end));
	}

	protected boolean supportsNestedBlockComments() {
		return false;
	}

	protected QuoteHolder getQuoteHolder(StringHolder stringHolder) {
		return getQuoteHolder(stringHolder, "N'", "B'", "'");
	}

	protected QuoteHolder getQuoteHolder(StringHolder stringHolder, String... args) {
		List<QuoteHolder> quoteHolders = CommonUtils.list();
		for (String arg : args) {
			QuoteHolder quoteHolder = searchQuote(stringHolder, arg);
			quoteHolders.add(quoteHolder);
		}
		QuoteHolder quoteHolder = quoteHolders.get(0);
		for (int i = 1; i < quoteHolders.size(); i++) {
			quoteHolder = quoteHolder.max(quoteHolders.get(i));
		}
		return quoteHolder;
	}

	static class QuoteHolder {
		public QuoteHolder() {
		};

		public QuoteHolder(String quote, int position) {
			this.position = position;
			this.quote = quote;
		};

		public String quote;
		public int position;

		QuoteHolder max(QuoteHolder holder) {
			if (this.position >= 0) {
				if (this.position < holder.position) {
					return this;
				} else {
					if (holder.position >= 0) {
						return holder;
					}
				}
			} else {
				if (holder.position > 0) {
					return holder;
				} else {
					return this;
				}
			}
			return this;
		}

		@Override
		public String toString() {
			ToStringBuilder builder = new ToStringBuilder();
			builder.add("quote", quote);
			builder.add("position", position);
			return builder.toString();
		}
	}

	private QuoteHolder searchQuote(StringHolder stringHolder, String quote) {
		int quotePos = stringHolder.searchStartQuote(quote);
		QuoteHolder holder = new QuoteHolder(quote, quotePos);
		return holder;
	}

	protected TextMarker getTextMarker(int position) {
		Map.Entry<Integer, TextMarker> entry = textMarkers.floorEntry(position);
		if (entry != null) {
			TextMarker marker = entry.getValue();
			if (marker.match(position)) {
				return marker;
			}
		}
		entry = textMarkers.ceilingEntry(position);
		if (entry != null) {
			TextMarker marker = entry.getValue();
			if (marker.match(position)) {
				return marker;
			}
		}
		return null;
	}

	/** Registers a half-open quoted-text or comment range for dialect tokenizers. */
	protected void addTextMarker(TextMarker marker) {
		textMarkers.put(marker.getStart(), marker);
	}

	public boolean hasNext() {
		if (!stringHolder.hasNext()) {
			return false;
		}
		int pos = stringHolder.getLineEnd();
		if (pos >= 0) {
			String text = stringHolder.getLine();
			comment = false;
			if (isChangeDelimiter(text, stringHolder)) {
				return this.hasNext();
			}
			if (text.trim().length() == 0) {
				stringHolder.setCurrent(text);
				stringHolder.nextLine();
				return this.hasNext();
			}
			int contentPosition = stringHolder.getPosition();
			while (contentPosition < stringHolder.getContextLength()
					&& Character.isWhitespace(stringHolder.charAt(contentPosition)))
				contentPosition++;
			TextMarker textMarker = this.getTextMarker(contentPosition);
			if (textMarker != null) {
				if (textMarker.getType().isComment()) {
					comment = true;
					stringHolder.setCurrent(textMarker.getValue());
					stringHolder.setPosition(textMarker.getEnd());
					return true;
				}
			}
			if (isStatementDelimiter(text, stringHolder)) {
				return true;
			}
			if (isStartStatement(text, stringHolder)) {
				return true;
			}
			handleElse(stringHolder);
			return true;
		} else {
			stringHolder.setPosition(stringHolder.getContextLength() + 1);
		}
		return false;
	}

	protected void setPosition(int pos) {
		if (pos >= 0) {
			stringHolder.setCurrent(stringHolder.substringAt(pos));
			stringHolder.setPosition(pos + 1);
		} else {
			pos = stringHolder.getContextLength();
			String value = stringHolder.substringAt(pos);
			stringHolder.setPosition(pos);
			stringHolder.setCurrent(value);
		}
	}

	protected void handleElse(StringHolder stringHolder) {
		int delPos = searchDelimiter();
		if (delPos >= 0) {
			handleSimpleStatement(delPos, stringHolder);
		} else {
			setPosition(delPos);
		}
	}

	protected int searchDelimiter() {
		int start = stringHolder.getPosition();
		while (true) {
			int pos = stringHolder.indexOf(this.getCurrentDelimiter(), start);
			if (pos >= 0) {
				TextMarker textMarker = this.getTextMarker(pos);
				if (textMarker == null) {
					return pos;
				}
				start = textMarker.getEnd();
			} else {
				return pos;
			}
		}
	}

	/**
	 * @return the comment
	 */
	public boolean isComment() {
		return comment;
	}

	protected void handleSimpleStatement(int delPos, StringHolder stringHolder) {
		String value = stringHolder.substringAt(delPos);
		stringHolder.setPosition(delPos + this.getCurrentDelimiter().length());
		stringHolder.setCurrent(value);
	}

	protected boolean isLineComment(String text) {
		if (text.trim().startsWith("--")) {
			return true;
		}
		return false;
	}

	protected boolean isChangeDelimiter(String text, StringHolder stringHolder) {
		return false;
	}

	protected boolean isStartStatement(String text, StringHolder stringHolder) {
		return false;
	}

	protected boolean isStatementDelimiter(String text, StringHolder stringHolder) {
		return false;
	}

	/**
	 * @return the currentDelimiter
	 */
	protected String getCurrentDelimiter() {
		if (currentDelimiter == null) {
			return this.getDelimiter();
		}
		return currentDelimiter;
	}

	/**
	 * @param currentDelimiter the currentDelimiter to set
	 */
	protected void setCurrentDelimiter(String currentDelimiter) {
		this.currentDelimiter = currentDelimiter;
	}

	/**
	 * @return the delimiter
	 */
	protected String getDelimiter() {
		return delimiter;
	}

	/**
	 * @param delimiter the delimiter to set
	 */
	protected void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	public String next() {
		return stringHolder.getCurrent();
	}

	@Data
	public static class TextMarker {
		private int start;
		private int end;
		private Type type;
		private String value;

		public boolean match(int position) {
			return start <= position && position < end;
		}
	}

	protected static enum Type {
		QUOTED_STRING, MULTI_LINE_COMMENT() {
			@Override
			public boolean isComment() {
				return true;
			}
		},
		SINGLE_LINE_COMMENT() {
			@Override
			public boolean isComment() {
				return true;
			}
		},;

		public boolean isBlank() {
			return false;
		}

		public boolean isComment() {
			return false;
		}

	}

}
