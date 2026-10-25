/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class MySqlSqlSplitterRegressionTest {

	@Test
	void defaultModeQuotesAndInlineCommentsAreOpaque() {
		for (String body : new String[] { "SELECT `a;b` FROM t", "SELECT `a``;b` FROM t", "SELECT 'a\\'b;c'",
				"SELECT \"a;\\\"b\"", "SELECT 1 -- comment; ignored\n", "SELECT 1 # comment; ignored\n", "SELECT 3--2",
				"SELECT '#;-- /*'" }) {
			var parts = new MySqlSqlSplitter(null).parse(body + ";SELECT 2;");
			assertEquals(2, parts.size(), body);
			assertEquals(body.stripTrailing(), parts.get(0).getText());
			assertEquals("SELECT 2", parts.get(1).getText());
		}
	}

	@Test
	void delimiterDirectivesInsideStringsDoNotChangeDelimiter() {
		String body = "CREATE PROCEDURE p() BEGIN SELECT 'a;\nDELIMITER ;\nb'; SELECT `a;b`; END";
		var parts = new MySqlSqlSplitter(null).parse("DELIMITER //\n" + body + "//\nDELIMITER ;\nSELECT 2;");
		assertEquals(2, parts.size());
		assertEquals(body, parts.get(0).getText());
		assertEquals("SELECT 2", parts.get(1).getText());
	}

	@Test
	void unclosedQuotesFail() {
		for (String sql : new String[] { "SELECT `missing", "SELECT 'a\\'", "SELECT \"missing" })
			assertThrows(InvalidTextException.class, () -> new MySqlSqlSplitter(null).parse(sql));
	}

	@Test
	void emptyAndWhitespaceOnlyInputHaveNoParts() {
		for (String sql : new String[] { "", "\n", " \t\n" })
			assertEquals(0, new MySqlSqlSplitter(null).parse(sql).size());
	}

}
