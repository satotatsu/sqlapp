/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class SapHanaSqlSplitterRegressionTest {

	@Test
	void anonymousBlocksKeepInternalStatementsAndNestedControlFlow() {
		for (String body : new String[] { "DO BEGIN SELECT 1 FROM DUMMY; SELECT 2 FROM DUMMY; END",
				"DO (IN p INTEGER => 1) BEGIN BEGIN SELECT 'END;--' FROM DUMMY; END; END",
				"DO BEGIN IF 1 = 1 THEN WHILE 1 = 0 DO SELECT 1 FROM DUMMY; END WHILE; ELSE SELECT 2 FROM DUMMY; END IF; END",
				"DO BEGIN SELECT CASE WHEN 1 = 1 THEN 2 END + CASE WHEN 2 = 2 THEN 3 END FROM DUMMY; END",
				"DO BEGIN FOR i IN 1..2 DO SELECT CASE WHEN 1 = 1 THEN 2 ELSE 3 END FROM DUMMY; END FOR; END",
				"DO /* BEGIN; */ BEGIN SELECT \"END;\" FROM t; -- END;\nSELECT 1 FROM DUMMY; END" }) {
			var parts = new SapHanaSqlSplitter(null).parse(body + ";SELECT 3 FROM DUMMY;");
			assertEquals(2, parts.size(), body);
			assertEquals(body, parts.get(0).getText());
			assertEquals("SELECT 3 FROM DUMMY", parts.get(1).getText());
		}
	}

	@Test
	void finalDelimiterIsOptionalButBlockMustBeComplete() {
		assertEquals(1, new SapHanaSqlSplitter(null).parse("DO BEGIN SELECT 1 FROM DUMMY; END").size());
		for (String sql : new String[] { "DO BEGIN SELECT 1 FROM DUMMY;", "DO SELECT 1;",
				"DO BEGIN IF 1 = 1 THEN SELECT 1 FROM DUMMY; END IF;", "DO BEGIN END SELECT 2;" })
			assertThrows(InvalidTextException.class, () -> new SapHanaSqlSplitter(null).parse(sql));
	}

	@Test
	void resolverPreservesOlderSqlAndAnonymousBlocksForHana2AndCloud() {
		var resolver = com.sqlapp.data.db.dialect.DialectResolver.getInstance();
		assertEquals(2, resolver.getDialect("HDB", 1, 0, 0).createSqlSplitter()
				.parse("SELECT 1 FROM DUMMY;SELECT 2 FROM DUMMY;").size());
		for (int major : new int[] { 2, 4 })
			assertEquals(2, resolver.getDialect("HDB", major, 0, 0).createSqlSplitter()
					.parse("DO BEGIN SELECT 1 FROM DUMMY; END;SELECT 2 FROM DUMMY;").size());
	}

}
