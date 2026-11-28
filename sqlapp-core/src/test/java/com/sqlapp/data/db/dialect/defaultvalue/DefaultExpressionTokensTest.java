package com.sqlapp.data.db.dialect.defaultvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Type;

class DefaultExpressionTokensTest {

	@Test
	void tokenizesLiteralsIdentifiersAndSymbols() {
		assertEquals(List.of(new Token(Type.IDENTIFIER, "CONVERT"), new Token(Type.SYMBOL, "("),
				new Token(Type.QUOTED_IDENTIFIER, "date"), new Token(Type.SYMBOL, ","),
				new Token(Type.IDENTIFIER, "getdate"), new Token(Type.SYMBOL, "("), new Token(Type.SYMBOL, ")"),
				new Token(Type.SYMBOL, ")")), DefaultExpressionTokens.tokenize("CONVERT([date],getdate())"));
		assertEquals(List.of(new Token(Type.STRING, "a'b"), new Token(Type.SYMBOL, "::"),
				new Token(Type.IDENTIFIER, "character"), new Token(Type.IDENTIFIER, "varying")),
				DefaultExpressionTokens.tokenize("'a''b'::character varying"));
		assertEquals(List.of(new Token(Type.NATIONAL_STRING, "あ'b")), DefaultExpressionTokens.tokenize("N'あ''b'"));
		assertEquals(List.of(new Token(Type.NUMBER, "1.50e-3")), DefaultExpressionTokens.tokenize("1.50e-3"));
		assertEquals(List.of(new Token(Type.QUOTED_IDENTIFIER, "a\"b")), DefaultExpressionTokens.tokenize("\"a\"\"b\""));
	}

	@Test
	void rejectsUnterminatedInput() {
		assertNull(DefaultExpressionTokens.tokenize("'abc"));
		assertNull(DefaultExpressionTokens.tokenize("[abc"));
		assertNull(DefaultExpressionTokens.tokenize("N'abc"));
	}

	@Test
	void unwrapsOnlyEnclosingParentheses() {
		assertEquals(DefaultExpressionTokens.tokenize("0"), DefaultExpressionTokens.unwrap(DefaultExpressionTokens.tokenize("((0))")));
		final var sum = DefaultExpressionTokens.tokenize("(1)+(2)");
		assertEquals(sum, DefaultExpressionTokens.unwrap(sum));
		assertEquals("-5", DefaultExpressionTokens.number(DefaultExpressionTokens.tokenize("-5")));
		assertNull(DefaultExpressionTokens.number(DefaultExpressionTokens.tokenize("1+2")));
	}
}
