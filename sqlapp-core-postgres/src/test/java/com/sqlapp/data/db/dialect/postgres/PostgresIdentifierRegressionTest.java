/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PostgresIdentifierRegressionTest {
	@Test void quotesKeywordsAndLeadingDigitsWithoutChangingOrdinaryNames() {
		var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("postgres", 18, 0, null);
		assertEquals("\"unique\"", dialect.quote("unique"));
		assertEquals("\"user\"", dialect.quote("user"));
		assertEquals("\"123name\"", dialect.quote("123name"));
		assertEquals("ordinary_name", dialect.quote("ordinary_name"));
		assertEquals("\"Odd\"\"name\"", dialect.quote("Odd\"name"));
	}
}
