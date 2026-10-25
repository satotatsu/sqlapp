/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.util.FileUtils;

public class SqlSplitterTest extends AbstractTest {

	@Test
	public void testSplitTest1() {
		final String input = FileUtils.getResource(this, "test1.sql");
		final SqlSplitter splitter = new SqlSplitter();
		splitter.parse(input);
		final List<SplitResult> list = splitter.getStatements();
		assertEquals("SELECT * FROM AAA", list.get(0).getText());
		assertEquals(1, list.size());
	}

	@Test
	public void testSplitTest4() {
		final String input = FileUtils.getResource(this, "test4.sql");
		final SqlSplitter splitter = new SqlSplitter();
		splitter.parse(input);
		final List<SplitResult> list = splitter.getStatements();
		assertEquals(5, list.size());
	}
	@Test
	public void adjacentStatementsRetainFirstCharacter() {
		List<SplitResult> parts = new SqlSplitter().parse("SELECT 1;SELECT 2;SELECT 3");
		assertEquals(3, parts.size());
		assertEquals("SELECT 2", parts.get(1).getText());
		assertEquals("SELECT 3", parts.get(2).getText());
	}

	@Test
	public void adjacentStatementsWithMultiCharacterDelimiter() {
		SqlSplitter splitter = new SqlSplitter() {
			@Override
			protected SqlTokenizer createSqlTokenizer(String input) {
				return new SqlTokenizer(input) {
					{ setDelimiter("//"); }
				};
			}
		};
		List<SplitResult> parts = splitter.parse("SELECT 1//SELECT 2//");
		assertEquals(2, parts.size());
		assertEquals("SELECT 1", parts.get(0).getText());
		assertEquals("SELECT 2", parts.get(1).getText());
	}


	@Test
	public void quotedTokensAndInlineCommentsProtectDelimiters() {
		for (String body : new String[] { "SELECT \"a;b\" FROM t", "SELECT 'a'';b'",
				"SELECT 1 -- comment; ignored\n", "SELECT 1 /* comment; ' \" */",
				"SELECT '--;/*'", "SELECT \"a\"\";b\" FROM t" }) {
			var parts = new SqlSplitter().parse(body + ";SELECT 2;");
			assertEquals(2, parts.size(), body);
			assertEquals(body.stripTrailing(), parts.get(0).getText());
			assertEquals("SELECT 2", parts.get(1).getText());
		}
	}

	@Test
	public void incompleteLexicalTokensFail() {
		for (String sql : new String[] { "SELECT 'missing", "SELECT \"missing", "SELECT 1 /* missing" })
			org.junit.jupiter.api.Assertions.assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
					() -> new SqlSplitter().parse(sql));
	}

	@Test
	public void emptyAndWhitespaceOnlyInputHaveNoParts() {
		for (String sql : new String[] { "", "\n", "\n\n", " \t\n" })
			assertEquals(0, new SqlSplitter().parse(sql).size());
	}

}
