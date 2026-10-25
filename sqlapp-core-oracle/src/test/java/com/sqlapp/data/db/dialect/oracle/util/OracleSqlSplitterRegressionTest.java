/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class OracleSqlSplitterRegressionTest {

	@Test
	void alternativeQuotesProtectDelimitersAndComments() {
		for (String body : new String[] { "SELECT q'[it's;a]' FROM dual", "SELECT Q'{a';--/*}' FROM dual",
				"SELECT nq'(a';b)' FROM dual", "SELECT q'<a';b>' FROM dual", "SELECT q'!a';b!' FROM dual" }) {
			var parts = new OracleSqlSplitter(null).parse(body + ";SELECT 2 FROM dual;");
			assertEquals(2, parts.size(), body);
			assertEquals(body, parts.get(0).getText());
			assertEquals("SELECT 2 FROM dual", parts.get(1).getText());
		}
	}

	@Test
	void slashInAlternativeQuoteIsNotABlockTerminator() {
		String body = "BEGIN v := q'[it's\n/\n;a]'; END;";
		var parts = new OracleSqlSplitter(null).parse(body + "\n/\nSELECT 2 FROM dual;");
		assertEquals(2, parts.size());
		assertEquals(body, parts.get(0).getText().stripTrailing());
	}

	@Test
	void unclosedAlternativeQuoteFails() {
		assertThrows(InvalidTextException.class, () -> new OracleSqlSplitter(null).parse("SELECT q'[missing;"));
	}

	@Test
	void resolverPreservesLegacySqlAndAlternativeQuotesFrom10g() {
		var resolver = com.sqlapp.data.db.dialect.DialectResolver.getInstance();
		assertEquals(2, resolver.getDialect("Oracle", 9, 0, 0).createSqlSplitter()
				.parse("SELECT 'a;b' FROM dual;SELECT 2 FROM dual;").size());
		for (int major : new int[] { 10, 11, 19, 23 })
			assertEquals(2, resolver.getDialect("Oracle", major, 0, 0).createSqlSplitter()
					.parse("SELECT q'[it's;a]' FROM dual;SELECT 2 FROM dual;").size());
	}

}
