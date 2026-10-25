/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class HsqlSqlSplitterRegressionTest {

	@Test
	void quotedIdentifiersAndInlineCommentsStayTogether() {
		for (String body : new String[] { "SELECT \"a;b\" FROM t", "SELECT 1 -- comment; ignored\n" }) {
			var parts = new HsqlSqlSplitter(null).parse(body + ";SELECT 2;");
			assertEquals(2, parts.size());
			assertEquals(body.stripTrailing(), parts.get(0).getText());
			assertEquals("SELECT 2", parts.get(1).getText());
		}
	}

}
