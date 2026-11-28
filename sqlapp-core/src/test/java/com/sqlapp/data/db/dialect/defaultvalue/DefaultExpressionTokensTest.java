package com.sqlapp.data.db.dialect.defaultvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Replacement;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Type;

class DefaultExpressionTokensTest {

	private static List<String> describe(final List<Token> tokens) {
		return tokens.stream().map(token -> token.type() + ":" + token.text()).toList();
	}

	@Test
	void tokenizesLiteralsIdentifiersAndSymbols() {
		assertEquals(List.of("IDENTIFIER:CONVERT", "SYMBOL:(", "QUOTED_IDENTIFIER:date", "SYMBOL:,",
				"IDENTIFIER:getdate", "SYMBOL:(", "SYMBOL:)", "SYMBOL:)"),
				describe(DefaultExpressionTokens.tokenize("CONVERT([date],getdate())")));
		assertEquals(List.of("STRING:a'b", "SYMBOL:::", "IDENTIFIER:character", "IDENTIFIER:varying"),
				describe(DefaultExpressionTokens.tokenize("'a''b'::character varying")));
		assertEquals(List.of("NATIONAL_STRING:あ'b"), describe(DefaultExpressionTokens.tokenize("N'あ''b'")));
		assertEquals(List.of("NUMBER:1.50e-3"), describe(DefaultExpressionTokens.tokenize("1.50e-3")));
		assertEquals(List.of("QUOTED_IDENTIFIER:a\"b"), describe(DefaultExpressionTokens.tokenize("\"a\"\"b\"")));
	}

	@Test
	void recordsSourcePositions() {
		final String sql = "x = N'a''b' + [c d]";
		final List<Token> tokens = DefaultExpressionTokens.tokenize(sql);
		assertEquals(List.of(new Token(Type.IDENTIFIER, "x", 0, 1), new Token(Type.SYMBOL, "=", 2, 3),
				new Token(Type.NATIONAL_STRING, "a'b", 4, 11), new Token(Type.SYMBOL, "+", 12, 13),
				new Token(Type.QUOTED_IDENTIFIER, "c d", 14, 19)), tokens);
		assertEquals("N'a''b' + [c d]", DefaultExpressionTokens.text(sql, tokens.subList(2, 5)));
	}

	@Test
	void skipsComments() {
		assertEquals(List.of("IDENTIFIER:a", "SYMBOL:-", "IDENTIFIER:b"),
				describe(DefaultExpressionTokens.tokenize("a /* x /* nested */ getdate() */ - b -- getdate()\n")));
		assertEquals(List.of("STRING:-- not a comment"),
				describe(DefaultExpressionTokens.tokenize("'-- not a comment'")));
		assertNull(DefaultExpressionTokens.tokenize("a /* unterminated"));
	}

	@Test
	void replacesRangesAndKeepsTheRest() {
		final String sql = "SET @a = getdate(); -- getdate()\nSET @b = getdate()";
		final List<Token> tokens = DefaultExpressionTokens.tokenize(sql);
		final var replacements = new java.util.ArrayList<Replacement>();
		for (int i = 0; i + 2 < tokens.size(); i++) {
			if (tokens.get(i).isKeyword("getdate") && tokens.get(i + 1).isSymbol("(") && tokens.get(i + 2).isSymbol(")")) {
				replacements.add(Replacement.of(tokens.subList(i, i + 3), "now()"));
			}
		}
		assertEquals("SET @a = now(); -- getdate()\nSET @b = now()", DefaultExpressionTokens.replace(sql, replacements));
		assertThrows(IllegalArgumentException.class, () -> DefaultExpressionTokens.replace("abc",
				List.of(new Replacement(0, 2, "x"), new Replacement(1, 3, "y"))));
		assertThrows(IllegalArgumentException.class,
				() -> DefaultExpressionTokens.replace("abc", List.of(new Replacement(2, 5, "x"))));
	}

	@Test
	void rejectsUnterminatedInput() {
		assertNull(DefaultExpressionTokens.tokenize("'abc"));
		assertNull(DefaultExpressionTokens.tokenize("[abc"));
		assertNull(DefaultExpressionTokens.tokenize("N'abc"));
	}

	@Test
	void unwrapsOnlyEnclosingParentheses() {
		assertEquals(describe(DefaultExpressionTokens.tokenize("0")),
				describe(DefaultExpressionTokens.unwrap(DefaultExpressionTokens.tokenize("((0))"))));
		final var sum = DefaultExpressionTokens.tokenize("(1)+(2)");
		assertEquals(sum, DefaultExpressionTokens.unwrap(sum));
		assertEquals("-5", DefaultExpressionTokens.number(DefaultExpressionTokens.tokenize("-5")));
		assertNull(DefaultExpressionTokens.number(DefaultExpressionTokens.tokenize("1+2")));
	}
}
