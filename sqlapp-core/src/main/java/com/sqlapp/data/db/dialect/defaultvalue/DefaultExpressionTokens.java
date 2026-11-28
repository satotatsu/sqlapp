package com.sqlapp.data.db.dialect.defaultvalue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Minimal tokenizer for recognizing SQL expressions.
 * <p>
 * It distinguishes string literals (optionally with an {@code N} prefix),
 * delimited identifiers ({@code "..."} and {@code [...]}), numbers, regular
 * identifiers and symbols. Line comments ({@code --}) and block comments,
 * including nested ones, are skipped. It never evaluates anything. Malformed input
 * (an unterminated literal, identifier or comment) yields {@code null}.
 * </p>
 * <p>
 * Each token keeps its position in the source text, so a recognized part of a
 * longer text (for example a function call inside a CHECK constraint or a
 * trigger body) can be replaced without touching the rest, using
 * {@link #replace(String, List)}.
 * </p>
 */
public final class DefaultExpressionTokens {

	private DefaultExpressionTokens() {
	}

	/** Token kinds. */
	public enum Type {
		STRING, NATIONAL_STRING, IDENTIFIER, QUOTED_IDENTIFIER, NUMBER, SYMBOL
	}

	/**
	 * A token.
	 *
	 * @param type  token kind
	 * @param text  unescaped value for strings and quoted identifiers, otherwise
	 *              the source text
	 * @param start index of the first source character
	 * @param end   index after the last source character
	 */
	public record Token(Type type, String text, int start, int end) {

		/** Whether this token is a regular or delimited identifier equal to the name, ignoring case. */
		public boolean isName(final String name) {
			return (type == Type.IDENTIFIER || type == Type.QUOTED_IDENTIFIER) && text.equalsIgnoreCase(name);
		}

		/** Whether this token is a regular (undelimited) identifier equal to the keyword, ignoring case. */
		public boolean isKeyword(final String keyword) {
			return type == Type.IDENTIFIER && text.equalsIgnoreCase(keyword);
		}

		/** Whether this token is the symbol. */
		public boolean isSymbol(final String symbol) {
			return type == Type.SYMBOL && text.equals(symbol);
		}

		/** Lower case text. */
		public String lower() {
			return text.toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Splits the SQL into tokens.
	 *
	 * @param sql expression
	 * @return tokens, or {@code null} when the input is malformed
	 */
	public static List<Token> tokenize(final String sql) {
		if (sql == null) {
			return null;
		}
		final List<Token> tokens = new ArrayList<>();
		final int length = sql.length();
		int i = 0;
		while (i < length) {
			final char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
				final int newline = sql.indexOf('\n', i);
				i = newline < 0 ? length : newline + 1;
			} else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
				final int end = commentEnd(sql, i);
				if (end < 0) {
					return null;
				}
				i = end;
			} else if ((c == 'N' || c == 'n') && i + 1 < length && sql.charAt(i + 1) == '\'') {
				final int end = closing(sql, i + 1, '\'');
				if (end < 0) {
					return null;
				}
				tokens.add(new Token(Type.NATIONAL_STRING, unescape(sql, i + 2, end, '\''), i, end + 1));
				i = end + 1;
			} else if (c == '\'' || c == '"' || c == '[') {
				final char close = c == '[' ? ']' : c;
				final int end = closing(sql, i, close);
				if (end < 0) {
					return null;
				}
				tokens.add(new Token(c == '\'' ? Type.STRING : Type.QUOTED_IDENTIFIER, unescape(sql, i + 1, end, close), i,
						end + 1));
				i = end + 1;
			} else if (Character.isDigit(c) || c == '.' && i + 1 < length && Character.isDigit(sql.charAt(i + 1))) {
				int end = i;
				while (end < length && (Character.isDigit(sql.charAt(end)) || sql.charAt(end) == '.')) {
					end++;
				}
				if (end < length && (sql.charAt(end) == 'e' || sql.charAt(end) == 'E')) {
					int exponent = end + 1;
					if (exponent < length && (sql.charAt(exponent) == '+' || sql.charAt(exponent) == '-')) {
						exponent++;
					}
					if (exponent < length && Character.isDigit(sql.charAt(exponent))) {
						end = exponent;
						while (end < length && Character.isDigit(sql.charAt(end))) {
							end++;
						}
					}
				}
				tokens.add(new Token(Type.NUMBER, sql.substring(i, end), i, end));
				i = end;
			} else if (Character.isLetter(c) || c == '_') {
				int end = i + 1;
				while (end < length) {
					final char next = sql.charAt(end);
					if (!Character.isLetterOrDigit(next) && next != '_' && next != '$' && next != '#' && next != '@') {
						break;
					}
					end++;
				}
				tokens.add(new Token(Type.IDENTIFIER, sql.substring(i, end), i, end));
				i = end;
			} else if (c == ':' && i + 1 < length && sql.charAt(i + 1) == ':') {
				tokens.add(new Token(Type.SYMBOL, "::", i, i + 2));
				i += 2;
			} else {
				tokens.add(new Token(Type.SYMBOL, String.valueOf(c), i, i + 1));
				i++;
			}
		}
		return Collections.unmodifiableList(tokens);
	}

	/**
	 * Removes parentheses enclosing the whole token list.
	 *
	 * @param tokens tokens
	 * @return tokens without enclosing parentheses
	 */
	public static List<Token> unwrap(final List<Token> tokens) {
		List<Token> current = tokens;
		while (current.size() >= 2 && current.getFirst().isSymbol("(") && current.getLast().isSymbol(")")
				&& closingIndex(current, 0) == current.size() - 1) {
			current = current.subList(1, current.size() - 1);
		}
		return current;
	}

	/**
	 * Returns the index of the parenthesis closing the one at {@code open}, or -1.
	 */
	public static int closingIndex(final List<Token> tokens, final int open) {
		int depth = 0;
		for (int i = open; i < tokens.size(); i++) {
			final Token token = tokens.get(i);
			if (token.isSymbol("(")) {
				depth++;
			} else if (token.isSymbol(")")) {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Returns a numeric literal text when the tokens are a number with an
	 * optional sign, otherwise {@code null}.
	 */
	public static String number(final List<Token> tokens) {
		if (tokens.size() == 1 && tokens.getFirst().type() == Type.NUMBER) {
			return tokens.getFirst().text();
		}
		if (tokens.size() == 2 && tokens.get(1).type() == Type.NUMBER
				&& (tokens.getFirst().isSymbol("-") || tokens.getFirst().isSymbol("+"))) {
			return (tokens.getFirst().isSymbol("-") ? "-" : "") + tokens.get(1).text();
		}
		return null;
	}

	/**
	 * Returns the source text covered by the tokens, from the first token's
	 * start to the last token's end.
	 *
	 * @param sql    source text the tokens were read from
	 * @param tokens non-empty contiguous tokens
	 * @return source text
	 */
	public static String text(final String sql, final List<Token> tokens) {
		if (tokens.isEmpty()) {
			return "";
		}
		return sql.substring(tokens.getFirst().start(), tokens.getLast().end());
	}

	/**
	 * Replacement of a source range.
	 *
	 * @param start index of the first replaced character
	 * @param end   index after the last replaced character
	 * @param text  replacement text
	 */
	public record Replacement(int start, int end, String text) {
		public Replacement {
			if (start < 0 || end < start) {
				throw new IllegalArgumentException("Invalid replacement range " + start + ".." + end);
			}
			java.util.Objects.requireNonNull(text, "text");
		}

		/** Replacement of the source range covered by the tokens. */
		public static Replacement of(final List<Token> tokens, final String text) {
			if (tokens.isEmpty()) {
				throw new IllegalArgumentException("No tokens to replace");
			}
			return new Replacement(tokens.getFirst().start(), tokens.getLast().end(), text);
		}
	}

	/**
	 * Applies replacements to the source text. Text outside the replaced ranges,
	 * including comments and whitespace, is kept as is.
	 *
	 * @param sql          source text
	 * @param replacements non-overlapping replacements in any order
	 * @return replaced text
	 * @throws IllegalArgumentException when ranges overlap or exceed the text
	 */
	public static String replace(final String sql, final List<Replacement> replacements) {
		final List<Replacement> sorted = new ArrayList<>(replacements);
		sorted.sort(java.util.Comparator.comparingInt(Replacement::start).thenComparingInt(Replacement::end));
		final StringBuilder builder = new StringBuilder(sql.length());
		int position = 0;
		for (final Replacement replacement : sorted) {
			if (replacement.start() < position || replacement.end() > sql.length()) {
				throw new IllegalArgumentException(
						"Overlapping or out-of-range replacement " + replacement.start() + ".." + replacement.end());
			}
			builder.append(sql, position, replacement.start()).append(replacement.text());
			position = replacement.end();
		}
		return builder.append(sql, position, sql.length()).toString();
	}

	/** Index after a possibly nested block comment starting at {@code start}, or -1. */
	private static int commentEnd(final String sql, final int start) {
		int depth = 0;
		int i = start;
		while (i + 1 < sql.length()) {
			if (sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
				depth++;
				i += 2;
			} else if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
				depth--;
				i += 2;
				if (depth == 0) {
					return i;
				}
			} else {
				i++;
			}
		}
		return -1;
	}

	private static int closing(final String value, final int start, final char close) {
		final int length = value.length();
		for (int i = start + 1; i < length; i++) {
			if (value.charAt(i) == close) {
				if (i + 1 < length && value.charAt(i + 1) == close) {
					i++;
					continue;
				}
				return i;
			}
		}
		return -1;
	}

	private static String unescape(final String value, final int start, final int end, final char close) {
		final String text = value.substring(start, end);
		final String doubled = String.valueOf(close) + close;
		return text.replace(doubled, String.valueOf(close));
	}
}
