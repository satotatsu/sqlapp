/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class H2SqlSplitterRegressionTest {

	@Test
	void dollarLiteralsAndAliasBodiesStayTogether() {
		for (String body : new String[] { "SELECT $$a;b$$", "SELECT $$a'b;/*--$$", "SELECT '$$;text'",
				"SELECT \"a;b\" FROM t", "CREATE ALIAS f AS $$String f() { return \"a;b\"; }$$" }) {
			var parts = new H2SqlSplitter(null).parse(body + ";SELECT 2;");
			assertEquals(2, parts.size(), body);
			assertEquals(body, parts.get(0).getText());
			assertEquals("SELECT 2", parts.get(1).getText());
		}
	}

	@Test
	void unclosedDollarLiteralFails() {
		assertThrows(InvalidTextException.class, () -> new H2SqlSplitter(null).parse("SELECT $$missing;"));
	}

	@Test
	void legacyAndCurrentResolversUseDollarAwareSplitting() {
		for (int[] version : new int[][] { { 1, 4 }, { 2, 0 }, { 2, 2 }, { 2, 3 } }) {
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("H2", version[0],
					version[1], 0);
			assertEquals(2, dialect.createSqlSplitter().parse("SELECT $$a;b$$;SELECT 2;").size());
		}
	}

}
