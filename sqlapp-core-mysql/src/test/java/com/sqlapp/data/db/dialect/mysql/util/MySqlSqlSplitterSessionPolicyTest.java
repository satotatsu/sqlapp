/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class MySqlSqlSplitterSessionPolicyTest {

	@Test
	void initialModesAreExplicitAndReusable() {
		var splitter = new MySqlSqlSplitter(null);
		splitter.setNoBackslashEscapes(true);
		assertTrue(splitter.isNoBackslashEscapes());
		for (int i = 0; i < 2; i++)
			assertEquals(2, splitter.parse("SELECT 'a\\';SELECT 2;").size());
		splitter.setNoBackslashEscapes(false);
		splitter.setAnsiQuotes(true);
		assertTrue(splitter.isAnsiQuotes());
		assertThrows(InvalidTextException.class, () -> splitter.parse("SET \"sql_mode\"='';"));
		assertEquals(2, splitter.parse("SELECT \"a\\\" FROM t;SELECT 2;").size());
		assertEquals(2, splitter.parse("SELECT 'a\\'b;c';SELECT 2;").size());
	}

	@Test
	void modeChangesRequireSeparateConfiguredSegments() {
		for (String sql : new String[] { "SET sql_mode='NO_BACKSLASH_ESCAPES';SELECT 'a\\';",
				"SET SESSION sql_mode = CONCAT(@@sql_mode, ',ANSI_QUOTES');", "SET @@session.sql_mode = DEFAULT",
				"SET `sql_mode`='';", "SET @saved=@@sql_mode, sql_mode='';", "SET GLOBAL sql_mode='';",
				"/*!40101 SET SQL_MODE='NO_BACKSLASH_ESCAPES' */;", "/*M!100100 SET sql_mode='' */;" }) {
			var failure = assertThrows(InvalidTextException.class, () -> new MySqlSqlSplitter(null).parse(sql), sql);
			assertTrue(failure.getMessage().contains("fixed sql_mode"));
		}
		for (String sql : new String[] { "SELECT 'SET sql_mode=1';", "-- SET sql_mode=''\nSELECT 1;",
				"SET @saved=@@sql_mode;", "SET @x=`a,sql_mode=1`;", "SET @x='a,sql_mode=1';",
				"SELECT 1 /* SET sql_mode='' */;" })
			assertFalse(new MySqlSqlSplitter(null).parse(sql).isEmpty());
	}

}
