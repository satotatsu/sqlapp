/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.vertica.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.dialect.vertica.DialectHolder;

public class VerticaSqlSplitterTest extends AbstractTest {
	Dialect dialect = DialectHolder.defaultDialect;

	@Test
	void generatedFunctionWithNestedCaseRemainsOneStatement() {
		var function = new com.sqlapp.data.schemas.Function("f");
		function.getReturning().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
		function.setStatement("RETURN CASE WHEN 1=1 THEN CASE WHEN 2=2 THEN 3 ELSE 4 END ELSE 5 END");
		String ddl = dialect.createSqlFactoryRegistry().createSql(function, com.sqlapp.data.db.sql.SqlType.CREATE)
				.get(0).getSqlText();
		var statements = dialect.createSqlSplitter().parse(ddl + "\nSELECT 9;");
		assertEquals(2, statements.size());
		assertEquals(ddl.substring(0, ddl.length() - 1), statements.get(0).getText().trim());
		assertEquals("SELECT 9", statements.get(1).getText().trim());
	}

	@Test
	void inlineFunctionsIgnoreQuotedAndCommentedKeywordsAndDelimiters() {
		String ddl = "CREATE /* header */ OR REPLACE FUNCTION \"BEGIN;END\"() RETURN VARCHAR AS BEGIN "
				+ "RETURN CASE WHEN 1=1 THEN 'END;''BEGIN' /* END; BEGIN */ ELSE $$BEGIN; END;$$ END; END;";
		var statements = dialect.createSqlSplitter().parse(ddl + " SELECT 9;");
		assertEquals(2, statements.size());
		assertEquals(ddl.substring(0, ddl.length() - 1), statements.get(0).getText().trim());
		assertEquals("SELECT 9", statements.get(1).getText().trim());
	}

	@Test
	void dollarQuotedProcedureAndStandaloneStringsRetainInternalSemicolons() {
		String procedure = "CREATE PROCEDURE p() LANGUAGE PLvSQL AS $$ BEGIN RAISE NOTICE 'it''s; END'; END; $$";
		var statements = dialect.createSqlSplitter().parse(procedure + "; SELECT $$Fred's; car$$; SELECT 9;");
		assertEquals(3, statements.size());
		assertEquals(procedure, statements.get(0).getText().trim());
		assertEquals("SELECT $$Fred's; car$$", statements.get(1).getText().trim());
	}

	@Test
	void malformedBodiesFailAndUnrelatedDdlDoesNotConsumeNextFunction() {
		assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> dialect.createSqlSplitter().parse("SELECT $$unterminated;"));
		assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> dialect.createSqlSplitter().parse("CREATE FUNCTION f() RETURN INT AS BEGIN RETURN 1;"));
		String function = "CREATE FUNCTION f() RETURN INT AS BEGIN RETURN 1; END";
		var statements = dialect.createSqlSplitter().parse("CREATE TABLE t(id INT); " + function + "; SELECT 2;");
		assertEquals(3, statements.size());
		assertEquals(function, statements.get(1).getText().trim());
		var eof = dialect.createSqlSplitter().parse(function);
		assertEquals(1, eof.size());
		assertTrue(eof.get(0).getText().trim().endsWith("END"));
	}

	@Test
	public void testSplitTest1() {
		String text = getResource("test.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(9, splits.size());
		int i = 0;
		assertEquals("/*create table comment*/", splits.get(i++).getText());
	}

}
