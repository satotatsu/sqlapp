/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;

public class OracleSqlSplitterTest extends AbstractTest {

	private Dialect dialect = DialectHolder.defaultDialect;

	@Test
	public void test1() {
		String text = this.getResource("test1.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(7, splits.size());
		int i = 0;
		assertEquals(
				"CREATE TABLE employee (id INT, \n                       name VARCHAR(10), \n                       salary DECIMAL(9,2))",
				splits.get(i++).getText());
	}

	@Test
	void anonymousBlocksRetainTheirRequiredSemicolon() {
		String block = "DECLARE\n n NUMBER;\nBEGIN\n n := 1;\n IF n=1 THEN NULL; END IF;\nEND;";
		var statements = dialect.createSqlSplitter().parse(block + "\n/\nSELECT 1 FROM DUAL;");
		assertEquals(2, statements.size());
		assertEquals(block, statements.get(0).getText());
		assertEquals("SELECT 1 FROM DUAL", statements.get(1).getText());
	}

	@Test
	void slashLinesInsideLiteralsAndCommentsAreNotTerminators() {
		String block = "BEGIN\n DBMS_OUTPUT.PUT_LINE('line\n/\ntext');\n /* comment\n/\n */\n NULL;\nEND;";
		var statements = dialect.createSqlSplitter().parse(block + "\n/\n");
		assertEquals(1, statements.size());
		assertEquals(block, statements.get(0).getText());
	}

	@Test
	void missingAnonymousBlockTerminatorFailsClearly() {
		assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> dialect.createSqlSplitter().parse("BEGIN NULL; END;"));
	}

}
