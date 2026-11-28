package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * String literal and identifier output for PostgreSQL.
 */
class PostgresStringLiteralTest {
	private final Dialect dialect = DialectResolver.getInstance().getDialect("postgres", 16, 0, null);

	@Test
	void plainValueUsesStandardLiteral() {
		assertEquals("'O''Reilly'", dialect.toStringLiteral("O'Reilly"));
	}

	@Test
	void backslashUsesEscapeStringSyntax() {
		// Independent of standard_conforming_strings.
		assertEquals("E'a\\\\'' OR 1=1 --'", dialect.toStringLiteral("a\\' OR 1=1 --"));
		assertEquals("E'C:\\\\tmp'", dialect.toStringLiteral("C:\\tmp"));
		assertThrows(IllegalArgumentException.class, () -> dialect.toStringLiteral("a\0b"));
	}

	@Test
	void identifierIsAlwaysQuoted() {
		assertEquals("\"a\"\"b\"", dialect.quoteIdentifier("a\"b"));
		assertEquals("\"x\"", dialect.quoteIdentifier("x"));
	}

	@Test
	void outputVariableUsesDialect() {
		assertEquals("SELECT E'a\\\\b'", SqlParser.getInstance().parse(dialect, "SELECT /*#v;literal*/")
				.eval(Map.of("v", "a\\b")).getSql().trim());
	}
}
