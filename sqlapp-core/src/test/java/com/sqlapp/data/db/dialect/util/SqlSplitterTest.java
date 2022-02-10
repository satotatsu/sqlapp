/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.test.AbstractTest;

public class SqlSplitterTest extends AbstractTest{

	@Test
	public void testSplitTest1() {
		String input = getResource("test1.sql");
		SqlSplitter splitter=new SqlSplitter();
		splitter.parse(input);
		List<SplitResult> list=splitter.getStatements();
		assertEquals("SELECT * FROM AAA", list.get(0).getText());
		assertEquals(1, list.size());
	}

	
	@Test
	public void testSplitTest4() {
		String input = getResource("test4.sql");
		SqlSplitter splitter=new SqlSplitter();
		splitter.parse(input);
		List<SplitResult> list=splitter.getStatements();
		assertEquals(5, list.size());
	}
}
