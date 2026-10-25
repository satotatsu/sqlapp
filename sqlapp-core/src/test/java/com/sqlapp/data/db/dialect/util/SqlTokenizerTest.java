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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;
import com.sqlapp.util.FileUtils;

public class SqlTokenizerTest extends AbstractTest {

	@Test
	public void testHasNext() {
		final SqlTokenizer sqlTokenizer = new SqlTokenizer(FileUtils.getResource(this, "split.sql"));
		sqlTokenizer.hasNext();
		String value = sqlTokenizer.next();
		assertEquals("use master/*#schemaNameSuffix*/", value);
		assertFalse(sqlTokenizer.isComment());
		sqlTokenizer.hasNext();
		value = sqlTokenizer.next();
		assertEquals("SET SESSION FOREIGN_KEY_CHECKS=0", value);
		assertFalse(sqlTokenizer.isComment());
		sqlTokenizer.hasNext();
		value = sqlTokenizer.next();
		assertEquals("/* Create Tables */", value);
		assertTrue(sqlTokenizer.isComment());
		sqlTokenizer.hasNext();
		value = sqlTokenizer.next();
		assertEquals(
				"CREATE TABLE apps\n(\n	id bigint NOT NULL,\n	created_at datetime,\n	updated_at datetime,\n	PRIMARY KEY (id),\n	UNIQUE (created_at)\n)",
				value);
		assertFalse(sqlTokenizer.isComment());
		sqlTokenizer.hasNext();
		value = sqlTokenizer.next();
		assertEquals("--SET IDENTITY_INSERT apps OFF;", value);
		assertTrue(sqlTokenizer.isComment());
		sqlTokenizer.hasNext();
		value = sqlTokenizer.next();
		assertEquals(
				"CREATE TABLE app_icons\n(\n	id bigint NOT NULL,\n	binary_data mediumblob NOT NULL,\n	PRIMARY KEY (id)\n)",
				value);
		assertEquals(false, sqlTokenizer.hasNext());
		assertFalse(sqlTokenizer.isComment());
	}

	@Test
	public void test2() {
		final SqlTokenizer sqlTokenizer = new SqlTokenizer("    \nGO  \n");
		sqlTokenizer.hasNext();
		String value = sqlTokenizer.next();
		assertEquals("GO  \n", value);
	}

	@Test
	public void existingWholeLineCommentHookRemainsEffective() {
		SqlTokenizer tokenizer = new SqlTokenizer("// legacy; comment\nSELECT 1;") {
			@Override
			protected boolean isLineComment(String text) {
				return text.startsWith("//") || super.isLineComment(text);
			}
		};
		assertTrue(tokenizer.hasNext());
		assertTrue(tokenizer.isComment());
		assertEquals("// legacy; comment", tokenizer.next());
		assertTrue(tokenizer.hasNext());
		assertFalse(tokenizer.isComment());
		assertEquals("SELECT 1", tokenizer.next());
	}

	@Test
	public void lexicalNotificationsPreserveQuotedTextAndMaskComments() {
		var statements = new java.util.ArrayList<String>();
		var comments = new java.util.ArrayList<String>();
		new SqlTokenizer("-- lead;\nSELECT 'a;b' /* ; */;SELECT \"x;y\";") {
			@Override
			protected void onStatement(String statement) {
				if (!statement.isBlank())
					statements.add(statement.strip());
			}
			@Override
			protected void onComment(String comment) {
				comments.add(comment);
			}
		};
		assertEquals(java.util.List.of("SELECT 'a;b'", "SELECT \"x;y\""), statements);
		assertEquals(java.util.List.of("-- lead;", "/* ; */"), comments);
	}

}
