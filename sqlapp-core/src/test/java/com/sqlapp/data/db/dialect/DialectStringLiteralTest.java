package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Default string literal and identifier output of {@link Dialect}.
 */
class DialectStringLiteralTest {
	private final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	@Test
	void stringLiteralDoublesQuotes() {
		assertEquals("'O''Reilly'", dialect.toStringLiteral("O'Reilly"));
		assertEquals("''''", dialect.toStringLiteral("'"));
		assertEquals("''", dialect.toStringLiteral(""));
		assertNull(dialect.toStringLiteral(null));
	}

	@Test
	void stringLiteralRejectsBackslashAndNulByDefault() {
		assertThrows(IllegalArgumentException.class, () -> dialect.toStringLiteral("a\\b"));
		assertThrows(IllegalArgumentException.class, () -> dialect.toStringLiteral("a\0b"));
	}

	@Test
	void quoteIdentifierAlwaysQuotes() {
		assertEquals("\"abc\"", dialect.quoteIdentifier("abc"));
		assertEquals("\"a\"\"b\"", dialect.quoteIdentifier("a\"b"));
		assertEquals("\"\"\"x\"\"\"", dialect.quoteIdentifier("\"x\""));
		assertNull(dialect.quoteIdentifier(null));
	}
}
