/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Identifies the outer structure of one PostgreSQL {@code CREATE FUNCTION} or
 * {@code CREATE TRIGGER} statement returned by a converter.
 * <p>
 * Only the declaration is read: object kind, qualified names, arguments, return
 * type, language, timing, events, orientation and the called function. Function
 * bodies (dollar-quoted or string literals), comments and quoted identifiers
 * are tokenised so that their contents never change the result. The body is not
 * parsed and nothing here claims that the statement is valid or harmless. A
 * text with more than one top-level statement is rejected.
 */
final class PostgresCreateStatement {
	enum Kind {
		FUNCTION, TRIGGER
	}

	/**
	 * Schema-qualified name; {@code schema} is null when the name was not
	 * qualified.
	 */
	record QualifiedName(String schema, String name) {
		@Override
		public String toString() {
			return schema == null ? name : schema + "." + name;
		}
	}

	/**
	 * Result of a successful identification. Trigger-only and function-only fields
	 * are null for the other kind.
	 */
	record Parsed(Kind kind, boolean orReplace, QualifiedName name, int argumentCount, String returns, String language,
			boolean securityDefiner, String timing, List<String> events, String orientation, QualifiedName table,
			QualifiedName function) {
	}

	/** Raised when the text cannot be identified safely. */
	static final class Rejected extends Exception {
		private static final long serialVersionUID = 1L;

		Rejected(final String message) {
			super(message);
		}
	}

	private enum TokenType {
		WORD, QUOTED, STRING, SYMBOL
	}

	private record Token(TokenType type, String text) {
		boolean isWord(final String word) {
			return type == TokenType.WORD && text.equals(word);
		}

		boolean isSymbol(final char c) {
			return type == TokenType.SYMBOL && text.length() == 1 && text.charAt(0) == c;
		}

		boolean isIdentifier() {
			return type == TokenType.WORD || type == TokenType.QUOTED;
		}
	}

	private final List<Token> tokens;
	private int position;

	private PostgresCreateStatement(final List<Token> tokens) {
		this.tokens = tokens;
	}

	static Parsed parse(final String sql) throws Rejected {
		final List<List<Token>> statements = statements(tokenize(sql));
		if (statements.isEmpty()) {
			throw new Rejected("The file contains no SQL statement");
		}
		if (statements.size() > 1) {
			throw new Rejected("The file must contain exactly one top-level statement, found " + statements.size());
		}
		return new PostgresCreateStatement(statements.getFirst()).statement();
	}

	private Parsed statement() throws Rejected {
		expectWord("create", "The statement must start with CREATE");
		boolean orReplace = false;
		if (peekWord("or")) {
			position++;
			expectWord("replace", "Expected REPLACE after CREATE OR");
			orReplace = true;
		}
		if (peekWord("constraint")) {
			throw new Rejected("CREATE CONSTRAINT TRIGGER is not supported in this version");
		}
		if (peekWord("function")) {
			position++;
			return function(orReplace);
		}
		if (peekWord("trigger")) {
			position++;
			return trigger(orReplace);
		}
		throw new Rejected("Only CREATE FUNCTION and CREATE TRIGGER are accepted, found: CREATE "
				+ (position < tokens.size() ? tokens.get(position).text().toUpperCase(Locale.ROOT) : ""));
	}

	private Parsed function(final boolean orReplace) throws Rejected {
		final QualifiedName name = qualifiedName("function name");
		expectSymbol('(', "Expected the argument list after the function name");
		final int arguments = argumentCount();
		String returns = null;
		String language = null;
		boolean securityDefiner = false;
		boolean body = false;
		while (position < tokens.size()) {
			final Token token = tokens.get(position++);
			if (token.isWord("returns")) {
				if (returns != null) {
					throw new Rejected("RETURNS appears more than once");
				}
				final Token type = next("Expected the return type");
				if (type.isWord("setof") || type.isWord("table")) {
					throw new Rejected(
							"Only RETURNS trigger is accepted, found RETURNS " + type.text().toUpperCase(Locale.ROOT));
				}
				returns = type.text();
			} else if (token.isWord("language")) {
				final Token value = next("Expected the language name");
				language = value.type() == TokenType.WORD ? value.text()
						: value.type() == TokenType.STRING || value.type() == TokenType.QUOTED
								? value.text().toLowerCase(Locale.ROOT)
								: null;
			} else if (token.isWord("security") && peekWord("definer")) {
				position++;
				securityDefiner = true;
			} else if (token.isWord("as")) {
				final Token value = next("Expected the function body after AS");
				if (value.type() != TokenType.STRING) {
					throw new Rejected("The function body must be a string or dollar-quoted literal");
				}
				body = true;
			} else if (token.isWord("begin") && !body) {
				throw new Rejected(
						"SQL-standard function bodies are not accepted; use LANGUAGE plpgsql with AS $$...$$");
			}
		}
		if (!"trigger".equals(returns)) {
			throw new Rejected("A helper function must declare RETURNS trigger");
		}
		if (!"plpgsql".equals(language)) {
			throw new Rejected("A helper function must declare LANGUAGE plpgsql");
		}
		if (!body) {
			throw new Rejected("The function body (AS ...) is missing");
		}
		return new Parsed(Kind.FUNCTION, orReplace, name, arguments, returns, language, securityDefiner, null, null,
				null, null, null);
	}

	private Parsed trigger(final boolean orReplace) throws Rejected {
		final Token nameToken = next("Expected the trigger name");
		if (!nameToken.isIdentifier()) {
			throw new Rejected("Expected the trigger name");
		}
		if (position < tokens.size() && tokens.get(position).isSymbol('.')) {
			throw new Rejected("A PostgreSQL trigger name cannot be schema-qualified; it belongs to its table");
		}
		final QualifiedName name = new QualifiedName(null, nameToken.text());
		final String timing;
		if (peekWord("before") || peekWord("after")) {
			timing = tokens.get(position++).text().toUpperCase(Locale.ROOT);
		} else if (peekWord("instead")) {
			position++;
			expectWord("of", "Expected OF after INSTEAD");
			timing = "INSTEAD OF";
		} else {
			throw new Rejected("Expected BEFORE, AFTER or INSTEAD OF");
		}
		final List<String> events = new ArrayList<>();
		while (true) {
			final Token event = next("Expected a trigger event");
			if (event.isWord("insert") || event.isWord("delete") || event.isWord("truncate")) {
				events.add(event.text().toUpperCase(Locale.ROOT));
			} else if (event.isWord("update")) {
				events.add("UPDATE");
				if (peekWord("of")) {
					position++;
					do {
						if (!next("Expected a column name after UPDATE OF").isIdentifier()) {
							throw new Rejected("Expected a column name after UPDATE OF");
						}
					} while (consumeSymbol(','));
				}
			} else {
				throw new Rejected("Unexpected trigger event: " + event.text());
			}
			if (!peekWord("or")) {
				break;
			}
			position++;
		}
		expectWord("on", "Expected ON after the trigger events");
		final QualifiedName table = qualifiedName("table name");
		String orientation = "STATEMENT";
		QualifiedName function = null;
		while (position < tokens.size()) {
			final Token token = tokens.get(position++);
			if (token.isWord("for")) {
				if (peekWord("each")) {
					position++;
				}
				final Token value = next("Expected ROW or STATEMENT");
				if (value.isWord("row") || value.isWord("statement")) {
					orientation = value.text().toUpperCase(Locale.ROOT);
				} else {
					throw new Rejected("Expected ROW or STATEMENT after FOR EACH");
				}
			} else if (token.isWord("when")) {
				expectSymbol('(', "Expected ( after WHEN");
				skipParenthesised();
			} else if (token.isWord("execute")) {
				if (!(peekWord("function") || peekWord("procedure"))) {
					throw new Rejected("Expected FUNCTION or PROCEDURE after EXECUTE");
				}
				position++;
				function = qualifiedName("function called by the trigger");
				expectSymbol('(', "Expected the argument list of the called function");
				skipParenthesised();
				if (position < tokens.size()) {
					throw new Rejected("Unexpected text after the EXECUTE FUNCTION clause");
				}
			} else if (token.isWord("from") || token.isWord("deferrable") || token.isWord("initially")) {
				throw new Rejected("Constraint trigger options are not supported in this version");
			}
		}
		if (function == null) {
			throw new Rejected("The EXECUTE FUNCTION clause is missing");
		}
		return new Parsed(Kind.TRIGGER, orReplace, name, 0, null, null, false, timing, List.copyOf(events), orientation,
				table, function);
	}

	private QualifiedName qualifiedName(final String what) throws Rejected {
		final Token first = next("Expected the " + what);
		if (!first.isIdentifier()) {
			throw new Rejected("Expected the " + what);
		}
		if (!consumeSymbol('.')) {
			return new QualifiedName(null, first.text());
		}
		final Token second = next("Expected the " + what + " after the schema");
		if (!second.isIdentifier()) {
			throw new Rejected("Expected the " + what + " after the schema");
		}
		if (position < tokens.size() && tokens.get(position).isSymbol('.')) {
			throw new Rejected("A database-qualified " + what + " is not accepted");
		}
		return new QualifiedName(first.text(), second.text());
	}

	/** Counts top-level arguments; the opening parenthesis is already consumed. */
	private int argumentCount() throws Rejected {
		int depth = 1;
		int count = 0;
		boolean content = false;
		while (position < tokens.size()) {
			final Token token = tokens.get(position++);
			if (token.isSymbol('(')) {
				depth++;
			} else if (token.isSymbol(')')) {
				depth--;
				if (depth == 0) {
					return content ? count + 1 : 0;
				}
			} else if (token.isSymbol(',') && depth == 1) {
				count++;
			} else {
				content = true;
			}
		}
		throw new Rejected("Unclosed argument list");
	}

	private void skipParenthesised() throws Rejected {
		int depth = 1;
		while (position < tokens.size()) {
			final Token token = tokens.get(position++);
			if (token.isSymbol('(')) {
				depth++;
			} else if (token.isSymbol(')') && --depth == 0) {
				return;
			}
		}
		throw new Rejected("Unclosed parenthesis");
	}

	private Token next(final String message) throws Rejected {
		if (position >= tokens.size()) {
			throw new Rejected(message);
		}
		return tokens.get(position++);
	}

	private boolean peekWord(final String word) {
		return position < tokens.size() && tokens.get(position).isWord(word);
	}

	private void expectWord(final String word, final String message) throws Rejected {
		if (!peekWord(word)) {
			throw new Rejected(message);
		}
		position++;
	}

	private void expectSymbol(final char symbol, final String message) throws Rejected {
		if (!consumeSymbol(symbol)) {
			throw new Rejected(message);
		}
	}

	private boolean consumeSymbol(final char symbol) {
		if (position < tokens.size() && tokens.get(position).isSymbol(symbol)) {
			position++;
			return true;
		}
		return false;
	}

	private static List<List<Token>> statements(final List<Token> tokens) {
		final List<List<Token>> result = new ArrayList<>();
		List<Token> current = new ArrayList<>();
		for (final Token token : tokens) {
			if (token.isSymbol(';')) {
				if (!current.isEmpty()) {
					result.add(current);
				}
				current = new ArrayList<>();
			} else {
				current.add(token);
			}
		}
		if (!current.isEmpty()) {
			result.add(current);
		}
		return result;
	}

	/**
	 * PostgreSQL lexical rules needed to find top-level tokens: unquoted words are
	 * folded to lower case, quoted identifiers keep their spelling, comments
	 * (nested block comments included) are skipped, and string, escape-string and
	 * dollar-quoted literals become single tokens.
	 */
	static List<Token> tokenize(final String sql) throws Rejected {
		final List<Token> tokens = new ArrayList<>();
		final int length = sql.length();
		int i = 0;
		while (i < length) {
			final char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
				final int end = sql.indexOf('\n', i);
				i = end < 0 ? length : end + 1;
			} else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
				i = skipBlockComment(sql, i);
			} else if (c == '"') {
				final StringBuilder text = new StringBuilder();
				int j = i + 1;
				while (true) {
					if (j >= length) {
						throw new Rejected("Unterminated quoted identifier");
					}
					if (sql.charAt(j) == '"') {
						if (j + 1 < length && sql.charAt(j + 1) == '"') {
							text.append('"');
							j += 2;
							continue;
						}
						break;
					}
					text.append(sql.charAt(j++));
				}
				if (text.isEmpty()) {
					throw new Rejected("Empty quoted identifier");
				}
				tokens.add(new Token(TokenType.QUOTED, text.toString()));
				i = j + 1;
			} else if (c == '\'') {
				final int end = skipString(sql, i + 1, false);
				tokens.add(new Token(TokenType.STRING, sql.substring(i + 1, end - 1)));
				i = end;
			} else if ((c == 'e' || c == 'E') && i + 1 < length && sql.charAt(i + 1) == '\'') {
				final int end = skipString(sql, i + 2, true);
				tokens.add(new Token(TokenType.STRING, sql.substring(i + 2, end - 1)));
				i = end;
			} else if (c == '$' && dollarTag(sql, i) != null) {
				final String tag = dollarTag(sql, i);
				final int end = sql.indexOf(tag, i + tag.length());
				if (end < 0) {
					throw new Rejected("Unterminated dollar-quoted string " + tag);
				}
				tokens.add(new Token(TokenType.STRING, sql.substring(i + tag.length(), end)));
				i = end + tag.length();
			} else if (Character.isLetter(c) || c == '_') {
				int end = i + 1;
				while (end < length && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_'
						|| sql.charAt(end) == '$')) {
					end++;
				}
				tokens.add(new Token(TokenType.WORD, sql.substring(i, end).toLowerCase(Locale.ROOT)));
				i = end;
			} else {
				tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c)));
				i++;
			}
		}
		return tokens;
	}

	private static int skipBlockComment(final String sql, final int start) throws Rejected {
		int depth = 0;
		int i = start;
		while (i < sql.length()) {
			if (sql.startsWith("/*", i)) {
				depth++;
				i += 2;
			} else if (sql.startsWith("*/", i)) {
				depth--;
				i += 2;
				if (depth == 0) {
					return i;
				}
			} else {
				i++;
			}
		}
		throw new Rejected("Unterminated block comment");
	}

	/**
	 * Returns the index after the closing quote; {@code start} is the first content
	 * character.
	 */
	private static int skipString(final String sql, final int start, final boolean escapes) throws Rejected {
		int i = start;
		while (i < sql.length()) {
			final char c = sql.charAt(i);
			if (escapes && c == '\\') {
				i += 2;
			} else if (c == '\'') {
				if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
					i += 2;
				} else {
					return i + 1;
				}
			} else {
				i++;
			}
		}
		throw new Rejected("Unterminated string literal");
	}

	/**
	 * Returns the {@code $tag$} starting at {@code start}, or null when it is not a
	 * dollar quote.
	 */
	private static String dollarTag(final String sql, final int start) {
		int i = start + 1;
		if (i < sql.length() && Character.isDigit(sql.charAt(i))) {
			return null;
		}
		while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
			i++;
		}
		return i < sql.length() && sql.charAt(i) == '$' ? sql.substring(start, i + 1) : null;
	}
}
