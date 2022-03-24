/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlite.util;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Sqlite;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.test.AbstractTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;


public class SqliteSqlSplitterTest extends AbstractTest{

	Dialect dialect=DialectUtils.getInstance(Sqlite.class);

	@Test
	public void test1() {
		String text=this.getResource("test.sql");
		SqlSplitter sqlSplitter=dialect.createSqlSplitter();
		List<SplitResult> splits=sqlSplitter.parse(text);
		assertEquals(8, splits.size());
		int i=0;
		assertEquals("/*create table comment*/", splits.get(i++).getText());
	}
	

}
