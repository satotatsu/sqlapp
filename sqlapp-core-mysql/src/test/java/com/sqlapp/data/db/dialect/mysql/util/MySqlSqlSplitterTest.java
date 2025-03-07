/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.mysql.MySql;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.dialect.util.SqlSplitter.TextType;

public class MySqlSqlSplitterTest extends AbstractTest {

	Dialect dialect = DialectUtils.getInstance(MySql.class);

	@Test
	public void test1() {
		String text = this.getResource("test1.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(9, splits.size());
		int i = 0;
		assertEquals(
				"CREATE TABLE employee (id INT, \n                       name VARCHAR(10), \n                       salary DECIMAL(9,2))",
				splits.get(i++).getText());
		assertEquals(TextType.COMMENT_DIRECTIVE, splits.get(4).getTextType());
	}

	@Test
	public void test2() {
		String text = this.getResource("001_parse_test.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(6, splits.size());
	}

	@Test
	public void test3() {
		String text = this.getResource("002_parse_test.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(6, splits.size());
	}

	@Test
	public void test4() {
		String text = this.getResource("003_parse_test.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(4, splits.size());
	}

}
