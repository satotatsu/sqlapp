package com.sqlapp.data.db.dialect.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * String literal and identifier output for MySQL.
 */
class MySqlStringLiteralTest {
	private final Dialect dialect = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);

	@Test
	void stringLiteralEscapesBackslashAndQuote() {
		assertEquals("'O''Reilly'", dialect.toStringLiteral("O'Reilly"));
		// a\' OR 1=1 -- must stay inside the literal
		assertEquals("'a\\\\'' OR 1=1 --'", dialect.toStringLiteral("a\\' OR 1=1 --"));
		assertEquals("'C:\\\\tmp'", dialect.toStringLiteral("C:\\tmp"));
		assertThrows(IllegalArgumentException.class, () -> dialect.toStringLiteral("a\0b"));
	}

	@Test
	void identifierIsAlwaysQuoted() {
		assertEquals("`a``b`", dialect.quoteIdentifier("a`b"));
		assertEquals("`x`", dialect.quoteIdentifier("x"));
		assertEquals("```x``; DROP TABLE t; --`", dialect.quoteIdentifier("`x`; DROP TABLE t; --"));
	}

	@Test
	void outputVariableUsesDialect() {
		assertEquals("SELECT 'a\\\\b'", SqlParser.getInstance().parse(dialect, "SELECT /*#v;literal*/")
				.eval(Map.of("v", "a\\b")).getSql().trim());
		assertEquals("SELECT * FROM `my table`", SqlParser.getInstance()
				.parse(dialect, "SELECT * FROM /*#t;identifier*/").eval(Map.of("t", "my table")).getSql().trim());
	}
}
