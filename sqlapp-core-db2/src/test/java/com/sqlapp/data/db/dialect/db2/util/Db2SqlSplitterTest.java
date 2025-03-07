/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.db2.Db2;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;

public class Db2SqlSplitterTest extends AbstractTest {

	Dialect dialect = DialectUtils.getInstance(Db2.class);

	@Test
	public void test1() {
		String text = getResource("test1.sql");
		// String text = FileUtils.getResource(this, "test1.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(15, splits.size());
	}

	@Test
	public void test2() {
		String text = getResource("test2.sql");
		// String text = FileUtils.getResource(this, "test2.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(14, splits.size());
	}
}
