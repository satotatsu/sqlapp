/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;

class MariadbSqlSplitterRegressionTest {
	@Test
	void inheritedMysqlQuotesAndCommentsStayOpaqueAcrossVersions() {
		for (int major : new int[] { 10, 11, 12 }) {
			var dialect = DialectResolver.getInstance().getDialect("MariaDB", major, 0, 0);
			for (String body : new String[] { "SELECT `a;b` FROM t", "SELECT 1 # comment; ignored\n" }) {
				var parts = dialect.createSqlSplitter().parse(body + ";SELECT 2;");
				assertEquals(2, parts.size());
				assertEquals(body.stripTrailing(), parts.get(0).getText());
				assertEquals("SELECT 2", parts.get(1).getText());
			}
		}
	}

	@Test
	void inheritedInitialSessionPolicyIsExplicit() {
		var dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 0, 0);
		var splitter = (com.sqlapp.data.db.dialect.mysql.util.MySqlSqlSplitter) dialect.createSqlSplitter();
		splitter.setNoBackslashEscapes(true);
		assertEquals(2, splitter.parse("SELECT 'a\\';SELECT 2;").size());
		org.junit.jupiter.api.Assertions.assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> splitter.parse("SET sql_mode='';SELECT 2;"));
	}

}
