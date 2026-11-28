/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class SqlServerSqlSplitterRegressionTest {

	@Test
	void goInQuotedTokensIsNotABatchBoundaryAndSplitterCanBeReused() {
		var splitter = new SqlServerSqlSplitter(null);
		for (String body : new String[] { "SELECT 'a\nGO\nb'", "SELECT [a\n GO\nb]", "SELECT \"a\nGO\nb\"",
				"SELECT [a]];b]" }) {
			var parts = splitter.parse(body + ";\n GO\nSELECT 2;\nGO 3\n");
			assertEquals(4, parts.size(), body);
			assertEquals(body, parts.get(0).getText());
			for (int i = 1; i < 4; i++)
				assertEquals("SELECT 2", parts.get(i).getText());
		}
	}

	@Test
	void goInNestedBlockCommentIsNotABatchBoundary() {
		String body = "SELECT 1;\n/* outer\nGO\n/* inner */\nGO\n*/\nSELECT 2";
		var parts = new SqlServerSqlSplitter(null).parse(body + ";\nGO\n");
		assertEquals(1, parts.size());
		assertEquals(body, parts.get(0).getText());
	}

	@Test
	void unclosedBracketIdentifierFails() {
		assertThrows(InvalidTextException.class, () -> new SqlServerSqlSplitter(null).parse("SELECT [missing\nGO\n"));
	}

	@Test
	void goWithLineCommentPreservesRepeatCountAndBatchContents() {
		var parts = new SqlServerSqlSplitter(null).parse("SELECT 1;\r\nGO -- end\r\nSELECT 2;\r\nGO 2 -- repeat");
		assertEquals(3, parts.size());
		assertEquals("SELECT 1", parts.get(0).getText());
		assertEquals("SELECT 2", parts.get(1).getText());
		assertEquals("SELECT 2", parts.get(2).getText());
	}
}
