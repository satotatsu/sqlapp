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
}
