/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;

public class PostgresSqlSplitterTest extends AbstractTest {

	private Dialect dialect = DialectHolder.defaultDialect;

	@Test
	public void test1() {
		String text = this.getResource("test1.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(8, splits.size());
		int i = 0;
		assertEquals(
				"CREATE TABLE employee (id INT, \n                       name VARCHAR(10), \n                       salary DECIMAL(9,2))",
				splits.get(i++).getText());
	}

	@Test
	public void anonymousBlocksAndDollarLiterals() {
		for (String body : new String[] { "DO $$ BEGIN PERFORM 1; BEGIN PERFORM 2; END; END $$",
				"DO LANGUAGE plpgsql $body$ BEGIN RAISE NOTICE 'it''s; /*'; END $body$",
				"DO $body$ BEGIN PERFORM $inner$a;b$inner$; END $body$ LANGUAGE plpgsql", "DO 'BEGIN PERFORM 1; END'",
				"SELECT $$a'b;--/*$$", "SELECT '$missing$;text'", "SELECT name$body$", "SELECT $名前$a;b$名前$",
				"CREATE FUNCTION f() RETURNS void AS $$BEGIN PERFORM 1; END$$ LANGUAGE plpgsql",
				"CREATE PROCEDURE p() LANGUAGE plpgsql AS $$BEGIN PERFORM 1; END$$", "SELECT \"a;b\"",
				"SELECT E'it\\'s;fine'", "SELECT 1 /* outer ; /* $missing$ */ ; */" }) {
			List<SplitResult> parts = dialect.createSqlSplitter().parse(body + ";SELECT 3;");
			assertEquals(2, parts.size(), body);
			assertEquals(body, parts.get(0).getText());
			assertEquals("SELECT 3", parts.get(1).getText());
		}
	}

	@Test
	public void commentsAndMissingTags() {
		List<SplitResult> parts = dialect.createSqlSplitter().parse("-- $missing$;\nDO $$BEGIN END$$;SELECT 3;");
		assertEquals(3, parts.size());
		assertEquals(SqlSplitter.TextType.COMMENT, parts.get(0).getTextType());
		for (String sql : new String[] { "DO $$ BEGIN;", "DO $body$ BEGIN; $BODY$;", "SELECT 'unclosed",
				"/* unclosed" }) {
			org.junit.jupiter.api.Assertions.assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
					() -> dialect.createSqlSplitter().parse(sql), sql);
		}
	}

	@Test
	public void olderDialectResolutionRetainsDollarQuotes() {
		var resolver = new com.sqlapp.data.db.dialect.postgres.resolver.PostgresDialectResolver.PostgresVersionResolver();
		for (int[] version : new int[][] { { 8, 0 }, { 8, 2 }, { 8, 4 }, { 9, 0 }, { 9, 6 }, { 15, 0 }, { 18, 0 } }) {
			Dialect resolved = resolver.getDialect(version[0], version[1], null);
			assertEquals(2, resolved.createSqlSplitter().parse("SELECT $$a;b$$;SELECT 3;").size());
			if (version[0] >= 9)
				assertEquals(2, resolved.createSqlSplitter().parse("DO $$BEGIN PERFORM 1; END$$;SELECT 3;").size());
		}
	}

}
